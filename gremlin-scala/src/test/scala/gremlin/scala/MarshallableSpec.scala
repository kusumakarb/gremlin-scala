package gremlin.scala

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import org.scalatest.WordSpec
import org.scalatest.Matchers
import java.lang.{Long ⇒ JLong, Double ⇒ JDouble}
import shapeless.test.illTyped
import GremlinPickler._

case class CCSimple(s: String, i: Int)
case class CCWithId(id: Option[Id[Int]], s: String) //id only set when adding to graph
case class CCWithJavaTypes(i: Integer, l: JLong, d: JDouble)

case class MyValueClass(value: Int) extends AnyVal
case class CCWithValueClass(s: String, i: MyValueClass)
case class CCWithOptionValueClass(s: String, i: Option[MyValueClass])

case class CCWithOption(i: Int, s: Option[String])

case class CCWithOptionId(s: String, @id id: Option[Int])

case class CCWithLabel(i: Int) extends WithLabel {
  def label = "my custom label"
}

case class NestedClass(s: String)

class NoneCaseClass(s: String)

class MarshallableSpec extends WordSpec with Matchers {

  "marshals case class to/from vertex" which {

    "only have simple members" in new Fixture {
      val cc = CCSimple("text", 12)
      val persisted = graph PLUS_NEW cc

      val v = graph.V(persisted.id).head
      v.label shouldBe cc.getClass.getSimpleName
      v.valueMap should contain("s" → cc.s)
      v.valueMap should contain("i" → cc.i)

      v.toCC_NEW[CCSimple] shouldBe cc
    }

    "contain java types" ignore new Fixture {
      val cc = CCWithJavaTypes(new Integer(12), new JLong(22l), new JDouble(3.3d))
      // TODO: implement
      // val persisted = graph PLUS_NEW cc

      // val v = graph.V(persisted.id).head
      // v.label shouldBe cc.getClass.getSimpleName
      // v.valueMap should contain("i" → cc.i)
      // v.valueMap should contain("l" → cc.l)
      // v.valueMap should contain("d" → cc.d)

      // v.toCC_NEW[CCWithJavaTypes] shouldBe cc
    }

    "contain the vertex id" in new Fixture {

      val cc = CCWithId(id = None, "some string")
      val persisted = graph PLUS_NEW cc

      val v = graph.V(persisted.id).head
      println(v + " " + v.valueMap)

      // TODO: replace CollectionsPickler with GremlinPickler everywhere - possible to have it in package object?
      // import GremlinPickler._
      val deserialised = v.toCC_NEW[CCWithId]
      deserialised.s shouldBe cc.s
      deserialised.id shouldBe Some(v.id)

      // write(Id("id value")) shouldBe Map("__id" → "id value")
      // read[Id[String]](Map("__id" → "id value")) shouldBe Id("id value")
    }

    "contain options" should {
      "map `Some[A]` to `A`" in new Fixture {
        val ccWithOptionSome = CCWithOption(Int.MaxValue, Some("optional value"))
        val persisted = graph PLUS_NEW ccWithOptionSome

        val v = graph.V(persisted.id).head
        v.value[String]("s") shouldBe ccWithOptionSome.s.get

        v.toCC_NEW[CCWithOption] shouldBe ccWithOptionSome
      }

      "map `None` to `null`" in new Fixture {
        val ccWithOptionNone = CCWithOption(Int.MaxValue, None)
        val persisted = graph PLUS_NEW ccWithOptionNone

        val v = graph.V(persisted.id).head
        v.keys should not contain "s" //None should be mapped to `null`

        v.toCC_NEW[CCWithOption] shouldBe ccWithOptionNone
      }

      // Background: if we marshal Option types, the graph db needs to understand scala.Option,
      // which wouldn't make any sense. So we rather translate it to `null` if it's `None`.
      // https://github.com/mpollmeier/gremlin-scala/issues/98
    }

    "contain value classes" should {
      "unwrap a simple value class" in new Fixture {
        val cc = CCWithValueClass("some text", MyValueClass(42))
        val persisted = graph PLUS_NEW cc

        val v = graph.V(persisted.id).head
        v.label shouldBe cc.getClass.getSimpleName
        v.valueMap should contain("s" → cc.s)
        v.valueMap should contain("i" → cc.i.value)

        v.toCC_NEW[CCWithValueClass] shouldBe cc
      }

      "unwrap an optional value class" in new Fixture {
        val cc = CCWithOptionValueClass("some text", Some(MyValueClass(42)))
        val persisted = graph PLUS_NEW cc

        val v = graph.V(persisted.id).head
        v.label shouldBe cc.getClass.getSimpleName
        v.valueMap should contain("s" → cc.s)
        v.valueMap should contain("i" → cc.i.get.value)

        v.toCC_NEW[CCWithOptionValueClass] shouldBe cc
      }

      "handle None value class" in new Fixture {
        val cc = CCWithOptionValueClass("some text", None)
        val persisted = graph PLUS_NEW cc

        val v = graph.V(persisted.id).head
        v.label shouldBe cc.getClass.getSimpleName
        v.valueMap should contain("s" → cc.s)
        v.valueMap.keySet should not contain ("i")

        v.toCC_NEW[CCWithOptionValueClass] shouldBe cc
      }
    }

    // TODO: 
    //   "define their custom marshaller" in new Fixture {
    //     val ccWithOptionNone = CCWithOption(Int.MaxValue, None)

    //     val marshaller = new Marshallable[CCWithOption] {
    //       def fromCC(cc: CCWithOption) =
    //         FromCC(None, "CCWithOption", Map("i" -> cc.i, "s" → cc.s.getOrElse("undefined")))

    //       def toCC(id: AnyRef, valueMap: Map[String, Any]): CCWithOption =
    //         CCWithOption(i = valueMap("i").asInstanceOf[Int],
    //                      s = valueMap.get("s").asInstanceOf[Option[String]])
    //     }

    //     val v = graph.+(ccWithOptionNone)(marshaller)
    //     v.toCC[CCWithOption](marshaller) shouldBe CCWithOption(ccWithOptionNone.i, Some("undefined"))
    //   }

    "specify a custom label" in new Fixture {
      val cc = CCWithLabel(10)
      val persisted = graph PLUS_NEW cc
      val v = graph.V(persisted.id).head
      v.label shouldBe "my custom label"
      v.valueMap should contain("i" → cc.i)

      v.toCC_NEW[CCWithLabel] shouldBe cc
    }

    // TODO: id fields
    //   "use @label and @id annotations" in new Fixture {
    //     val ccWithLabelAndId = CCWithLabelAndId(
    //       "some string",
    //       Int.MaxValue,
    //       Long.MaxValue,
    //       Some("option type"),
    //       Seq("test1", "test2"),
    //       Map("key1" → "value1", "key2" → "value2"),
    //       NestedClass("nested")
    //     )

    //     val v = graph + ccWithLabelAndId

    //     v.toCC[CCWithLabelAndId] shouldBe ccWithLabelAndId

    //     val vl = graph.V(v.id).head()
    //     vl.label shouldBe "the_label"
    //     vl.id shouldBe ccWithLabelAndId.id
    //     vl.valueMap should contain("s" → ccWithLabelAndId.s)
    //     vl.valueMap should contain("l" → ccWithLabelAndId.l)
    //     vl.valueMap should contain("o" → ccWithLabelAndId.o.get)
    //     vl.valueMap should contain("seq" → ccWithLabelAndId.seq)
    //     vl.valueMap should contain("map" → ccWithLabelAndId.map)
    //     vl.valueMap should contain("nested" → ccWithLabelAndId.nested)
    //   }

    //   "have an Option @id annotation" in new Fixture {
    //     val cc = CCWithOptionId("text", Some(12))
    //     val v = graph + cc

    //     v.toCC[CCWithOptionId] shouldBe cc

    //     val vl = graph.V(v.id).head()
    //     vl.label shouldBe cc.getClass.getSimpleName
    //     vl.id shouldBe cc.id.get
    //     vl.valueMap should contain("s" → cc.s)
    //   }

    // }

    // "find vertices by label" in new Fixture {
    //   val ccSimple = CCSimple("a string", 42)
    //   val ccWithOption = CCWithOption(52, Some("other string"))
    //   val ccWithLabel = CCWithLabel("s")

    //   graph + ccSimple
    //   graph + ccWithOption
    //   graph + ccWithLabel

    //   graph.V.count.head shouldBe 3

    //   val ccSimpleVertices = graph.V.hasLabel[CCSimple].toList
    //   ccSimpleVertices should have size 1
    //   ccSimpleVertices.head.toCC[CCSimple] shouldBe ccSimple

    //   val ccWithLabelVertices = graph.V.hasLabel[CCWithLabel].toList
    //   ccWithLabelVertices should have size 1
    //   ccWithLabelVertices.head.toCC[CCWithLabel] shouldBe ccWithLabel
    // }

    // "can't persist a none product type (none case class or tuple)" in {
    //   illTyped {
    //     """
    //       val graph = TinkerGraph.open.asScala
    //       graph + new NoneCaseClass("test")
    //     """
    //   }
  }

  trait Fixture {
    val graph = TinkerGraph.open.asScala
  }
}
