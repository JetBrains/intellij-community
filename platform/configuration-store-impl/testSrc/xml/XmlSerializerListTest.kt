// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.xml

import com.intellij.openapi.components.BaseState
import com.intellij.util.xmlb.annotations.*
import org.junit.jupiter.api.Test
import java.util.*

class XmlSerializerListTest {
  @Test
  fun nullCollection() {
    @Suppress("unused")
    class BeanWithCollection {
      @Property(surroundWithTag = false)
      @XCollection
      var collection: List<BeanWithProperty>? = null
    }
    testSerializer(
      expectedXml = "<BeanWithCollection />",
      expectedJson = """
        {
          "collection": null
        }
      """,
      expectedJsonByXml = """
        {}
      """,
      bean = BeanWithCollection(),
    )
  }

  @Test
  fun notFinalField() {
    @Tag("bean")
    class Bean {
      @JvmField
      var values = arrayListOf("a", "b", "w")
    }

    val bean = Bean()
    check(bean) {
      bean.values = it
    }
  }

  @Test
  fun `empty list`() {
    @Tag("bean")
    class Bean {
      @JvmField
      var values = emptyList<String>()
    }

    val data = Bean()
    data.values = listOf("foo")
    testSerializer(
      expectedXml = """
        <bean>
          <option name="values">
            <list>
              <option value="foo" />
            </list>
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "values": [
            "foo"
          ]
        }
      """,
      bean = data,
    )
  }

  @Test
  fun `empty java list`() {
    @Tag("bean")
    class Bean {
      @JvmField
      var values = Collections.emptyList<String>()
    }

    val data = Bean()
    data.values = listOf("foo")
    testSerializer(
      expectedXml = """
        <bean>
          <option name="values">
            <list>
              <option value="foo" />
            </list>
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "values": [
            "foo"
          ]
        }
      """,
      bean = data,
    )
  }

  @Test
  fun notFinalProperty() {
    @Tag("bean")
    class Bean {
      var values = arrayListOf("a", "b", "w")
    }

    val bean = Bean()
    check(bean) {
      bean.values = it
    }
  }

  @Test
  fun finalField() {
    @Tag("bean")
    class Bean {
      @JvmField
      val values = arrayListOf("a", "b", "w")
    }

    val bean = Bean()
    check(bean) {
      bean.values.clear()
      bean.values.addAll(it)
    }
  }

  @Test
  fun finalProperty() {
    @Tag("bean")
    class Bean {
      @OptionTag
      val values = arrayListOf("a", "b", "w")
    }

    val bean = Bean()
    check(bean) {
      bean.values.clear()
      bean.values.addAll(it)
    }
  }

  private data class SpecSource(var pathOrUrl: String? = null)

  @Test
  fun `final property and style v2`() {
    @Tag("bean")
    class Bean : BaseState() {
      @get:XCollection(style = XCollection.Style.v2)
      val specSources by list<SpecSource>()
    }

    val bean = Bean()

    testSerializer(
      expectedXml = """<bean />""",
      expectedJson = """{}""",
      bean = bean,
    )

    bean.specSources.clear()
    bean.specSources.addAll(listOf(SpecSource("foo"), SpecSource("bar")))

    testSerializer(
      expectedXml = """
        <bean>
          <specSources>
            <SpecSource>
              <option name="pathOrUrl" value="foo" />
            </SpecSource>
            <SpecSource>
              <option name="pathOrUrl" value="bar" />
            </SpecSource>
          </specSources>
        </bean>
      """,
      expectedJson = """
        {
          "specSources": [
            {
              "pathOrUrl": "foo"
            },
            {
              "pathOrUrl": "bar"
            }
          ]
        }
      """,
      bean = bean,
    )
  }

  @Suppress("SpellCheckingInspection")
  @Test
  fun elementTypes() {
    @Tag("selector")
    class Selector {
      @Attribute
      var name = ""
      @Attribute
      var path = ""
    }

    @Transient
    abstract class H2

    abstract class H : H2() {
      private val selectors = ArrayList<Selector>()

      // test mutable list (in-place mutation must not be performed)
      @Suppress("unused")
      @XCollection(propertyElementName = "selectors")
      fun getSelectors() = selectors.toMutableList()

      fun setSelectors(value: List<Selector>) {
        selectors.clear()
        selectors.addAll(value)
      }
    }

    class A : H()
    class B : H()
    class C : H()

    class Bean {
      @XCollection(elementTypes = [A::class, B::class, C::class])
      val handlers = ArrayList<H>()
    }

    val random = Random(42)
    fun s() = Selector().also { it.name = random.nextInt().toString(32); it.path = random.nextInt().toString(32) }

    val bean = Bean()
    bean.handlers.add(A().also { it.setSelectors(listOf(s(), s())) })
    bean.handlers.add(B().also { it.setSelectors(listOf(s(), s())) })
    bean.handlers.add(C().also { it.setSelectors(listOf(s(), s())) })
    testSerializer(
      expectedXml = """
        <Bean>
          <option name="handlers">
            <A>
              <selectors>
                <selector name="-12rsomb" path="6vt2nn" />
                <selector name="-18hgh0v" path="64bg18" />
              </selectors>
            </A>
            <B>
              <selectors>
                <selector name="17ggf74" path="-7d8h5l" />
                <selector name="13et7c3" path="-15d6ukj" />
              </selectors>
            </B>
            <C>
              <selectors>
                <selector name="-1apt5a2" path="bm234q" />
                <selector name="-cbp623" path="1pob5us" />
              </selectors>
            </C>
          </option>
        </Bean>
      """,
      expectedJson = """
        {
          "handlers": [
            {
              "_class": "A",
              "selectors": [
                {
                  "name": "-12rsomb",
                  "path": "6vt2nn"
                },
                {
                  "name": "-18hgh0v",
                  "path": "64bg18"
                }
              ]
            },
            {
              "_class": "B",
              "selectors": [
                {
                  "name": "17ggf74",
                  "path": "-7d8h5l"
                },
                {
                  "name": "13et7c3",
                  "path": "-15d6ukj"
                }
              ]
            },
            {
              "_class": "C",
              "selectors": [
                {
                  "name": "-1apt5a2",
                  "path": "bm234q"
                },
                {
                  "name": "-cbp623",
                  "path": "1pob5us"
                }
              ]
            }
          ]
        }
      """,
      bean = bean,
    )
  }

  @Test
  fun finalPropertyWithoutWrapping() {
    @Tag("bean")
    class Bean {
      @XCollection
      val values = arrayListOf("a", "b", "w")
    }

    val bean = Bean()
    testSerializer(
      expectedXml = """
        <bean>
          <option name="values">
            <option value="a" />
            <option value="b" />
            <option value="w" />
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "values": [
            "a",
            "b",
            "w"
          ]
        }
      """,
      bean = bean,
    )

    bean.values.clear()
    bean.values.addAll(listOf("1", "2", "3"))

    testSerializer(
      expectedXml = """
        <bean>
          <option name="values">
            <option value="1" />
            <option value="2" />
            <option value="3" />
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "values": [
            "1",
            "2",
            "3"
          ]
        }
      """,
      bean = bean,
    )
  }

  private fun <T : Any> check(bean: T, setter: (values: ArrayList<String>) -> Unit) {
    testSerializer(
      expectedXml = """
        <bean>
          <option name="values">
            <list>
              <option value="a" />
              <option value="b" />
              <option value="w" />
            </list>
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "values": [
            "a",
            "b",
            "w"
          ]
        }
      """,
      bean = bean,
    )
    setter(arrayListOf("1", "2", "3"))

    testSerializer(
      expectedXml = """
        <bean>
          <option name="values">
            <list>
              <option value="1" />
              <option value="2" />
              <option value="3" />
            </list>
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "values": [
            "1",
            "2",
            "3"
          ]
        }
      """,
      bean = bean,
    )
  }
}