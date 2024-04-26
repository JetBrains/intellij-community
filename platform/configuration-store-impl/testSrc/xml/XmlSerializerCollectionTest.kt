// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.xml

import com.intellij.configurationStore.deserialize
import com.intellij.openapi.components.BaseState
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.annotations.CollectionBean
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.jdom.Element
import org.junit.Test
import java.util.*

internal class XmlSerializerCollectionTest {
  @Test fun jdomExternalizableStringList() {
    @Tag("b")
    class Bean3 {
      @Suppress("DEPRECATION")
      var list = com.intellij.openapi.util.JDOMExternalizableStringList()
    }

    val bean = Bean3()
    bean.list.add("\u0001one")
    bean.list.add("two")
    bean.list.add("three")
    testSerializer(
      expectedXml = """
        <b>
          <list>
            <item value="one" />
            <item value="two" />
            <item value="three" />
          </list>
        </b>
      """,
      expectedJson = """
        {
          "list": [
            "one",
            "two",
            "three"
          ]
        }
      """,
      bean = bean,
      filter = SkipDefaultsSerializationFilter(),
    )
  }

  @Test
  fun jdomExternalizableStringListWithoutClassAttribute() {
    val testList = arrayOf("foo", "bar")
    val element = Element("test")
    val listElement = Element("list")
    element.addContent(listElement)
    for (id in testList) {
      listElement.addContent(Element("item").setAttribute("itemvalue", id))
    }

    val result = ArrayList<String>()
    @Suppress("DEPRECATION")
    com.intellij.openapi.util.JDOMExternalizableStringList.readList(result, element)
    assertThat(result).isEqualTo(testList.toList())
  }

  @Test fun collectionBean() {
    val bean = Bean4()
    bean.list.add("one")
    bean.list.add("two")
    bean.list.add("three")
    testSerializer(
      expectedXml = """
      <b>
        <list>
          <item value="one" />
          <item value="two" />
          <item value="three" />
        </list>
      </b>
      """,
      expectedJson = """
        {
          "list": [
            "one",
            "two",
            "three"
          ]
        }
      """,
      bean = bean,
      filter = SkipDefaultsSerializationFilter(),
    )
  }

  @Test fun collectionBeanReadJDOMExternalizableStringList() {
    @Suppress("DEPRECATION") val list = com.intellij.openapi.util.JDOMExternalizableStringList()
    list.add("one")
    list.add("two")
    list.add("three")

    val value = Element("value")
    list.writeExternal(value)
    val o = deserialize<Bean4>(Element("state").addContent(Element("option").setAttribute("name", "myList").addContent(value)))
    assertSerializer(o, "<b>\n" + "  <list>\n" + "    <item value=\"one\" />\n" + "    <item value=\"two\" />\n" + "    <item value=\"three\" />\n" + "  </list>\n" + "</b>", SkipDefaultsSerializationFilter())
  }

  @Test fun polymorphicArray() {
    @Tag("bean")
    class BeanWithPolymorphicArray {
      @Suppress("DEPRECATION")
      @com.intellij.util.xmlb.annotations.AbstractCollection(elementTypes = [(BeanWithPublicFields::class), (BeanWithPublicFieldsDescendant::class)])
      var v = arrayOf<BeanWithPublicFields>()
    }

    val bean = BeanWithPolymorphicArray()

    testSerializer(
      expectedXml = """
      <bean>
        <option name="v">
          <array />
        </option>
      </bean>
      """,
      expectedJson = """
        {
          "v": []
        }
      """,
      bean = bean,
    )

    bean.v = arrayOf(BeanWithPublicFields(), BeanWithPublicFieldsDescendant(), BeanWithPublicFields())

    testSerializer(
      expectedXml = """
        <bean>
          <option name="v">
            <array>
              <BeanWithPublicFields>
                <option name="INT_V" value="1" />
                <option name="STRING_V" value="hello" />
              </BeanWithPublicFields>
              <BeanWithPublicFieldsDescendant>
                <option name="NEW_S" value="foo" />
                <option name="INT_V" value="1" />
                <option name="STRING_V" value="hello" />
              </BeanWithPublicFieldsDescendant>
              <BeanWithPublicFields>
                <option name="INT_V" value="1" />
                <option name="STRING_V" value="hello" />
              </BeanWithPublicFields>
            </array>
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "v": [
            {
              "_class": "BeanWithPublicFields",
              "int_v": 1,
              "string_v": "hello"
            },
            {
              "_class": "BeanWithPublicFieldsDescendant",
              "new_s": "foo",
              "int_v": 1,
              "string_v": "hello"
            },
            {
              "_class": "BeanWithPublicFields",
              "int_v": 1,
              "string_v": "hello"
            }
          ]
        }
      """,
      bean = bean,
    )
  }

  @Test
  fun xCollection() {
    val bean = BeanWithArrayWithoutTagName()
    testSerializer(
      expectedXml = """
        <BeanWithArrayWithoutTagName>
          <option name="foo">
            <option value="a" />
          </option>
        </BeanWithArrayWithoutTagName>
      """,
      expectedJson = """
        {
          "foo": [
            "a"
          ]
        }
      """,
      bean = bean,
    )
  }

  @Test
  fun java9ImmutableSet() {
    @Suppress("unused")
    class Bean {
      @XCollection
      var foo = mutableSetOf("a", "b")
    }

    val bean = Bean()
    testSerializer(
      expectedXml = """
        <Bean>
          <option name="foo">
            <option value="a" />
            <option value="b" />
          </option>
        </Bean>
      """,
      expectedJson = """
        {
          "foo": [
            "a",
            "b"
          ]
        }
      """,
      bean = bean,
    )
  }

  @Test
  fun immutableCollections() {
    @Suppress("unused")
    class Bean {
      @XCollection
      val firstElement: List<String> = listOf("gradle")
      @XCollection
      val secondElement: List<String> = Collections.singletonList("maven")
    }

    val bean = Bean()
    testSerializer(
      expectedXml = """
        <Bean>
          <option name="firstElement">
            <option value="gradle" />
          </option>
          <option name="secondElement">
            <option value="maven" />
          </option>
        </Bean>
      """,
      expectedJson = """
        {
          "firstElement": [
            "gradle"
          ],
          "secondElement": [
            "maven"
          ]
        }
      """,
      bean = bean,
    )
  }

  @Test fun arrayAnnotationWithElementTag() {
    @Tag("bean")
    class Bean {
      @Suppress("DEPRECATION")
      @com.intellij.util.xmlb.annotations.AbstractCollection(elementTag = "vValue", elementValueAttribute = "v")
      var v = arrayOf("a", "b")
    }

    val bean = Bean()

    testSerializer(
      expectedXml = """
        <bean>
          <option name="v">
            <array>
              <vValue v="a" />
              <vValue v="b" />
            </array>
          </option>
        </bean>""",
      expectedJson = """
        {
          "v": [
            "a",
            "b"
          ]
        }
      """,
      bean = bean,
    )

    bean.v = arrayOf("1", "2", "3")

    testSerializer(
      expectedXml = """
        <bean>
          <option name="v">
            <array>
              <vValue v="1" />
              <vValue v="2" />
              <vValue v="3" />
            </array>
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "v": [
            "1",
            "2",
            "3"
          ]
        }
      """.trimIndent(),
      bean = bean,
    )
  }

  @Test fun arrayWithoutTag() {
    @Tag("bean")
    class Bean {
      @XCollection(elementName = "vValue", valueAttributeName = "v")
      var v = arrayOf("a", "b")
      @Suppress("PropertyName", "unused")
      var INT_V = 1
    }

    val bean = Bean()

    testSerializer(
      expectedXml = """
        <bean>
          <option name="INT_V" value="1" />
          <option name="v">
            <vValue v="a" />
            <vValue v="b" />
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "int_v": 1,
          "v": [
            "a",
            "b"
          ]
        }
      """,
      bean = bean,
    )

    bean.v = arrayOf("1", "2", "3")

    testSerializer(
      expectedXml = """
        <bean>
          <option name="INT_V" value="1" />
          <option name="v">
            <vValue v="1" />
            <vValue v="2" />
            <vValue v="3" />
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "int_v": 1,
          "v": [
            "1",
            "2",
            "3"
          ]
        }
      """,
      bean = bean,
    )
  }

  @Suppress("PropertyName")
  private data class BeanWithArray(@Suppress("ArrayInDataClass") var ARRAY_V: Array<String> = arrayOf("a", "b"))

  @Test fun array() {
    val bean = BeanWithArray()
    testSerializer(
      expectedXml = """
        <BeanWithArray>
        <option name="ARRAY_V">
          <array>
            <option value="a" />
            <option value="b" />
          </array>
        </option>
      </BeanWithArray>
      """,
      expectedJson = """
        {
          "array_v": [
            "a",
            "b"
          ]
        }
      """.trimIndent(),
      bean = bean,
    )

    bean.ARRAY_V = arrayOf("1", "2", "3", "")
    testSerializer(
      expectedXml = """
        <BeanWithArray>
          <option name="ARRAY_V">
            <array>
              <option value="1" />
              <option value="2" />
              <option value="3" />
              <option value="" />
            </array>
          </option>
        </BeanWithArray>
      """,
      expectedJson = """
        {
          "array_v": [
            "1",
            "2",
            "3",
            ""
          ]
        }
      """,
      bean = bean,
    )
  }

  @Test
  fun `string set with one value as default`() {
    class Bean : BaseState() {
      @get:XCollection(style = XCollection.Style.v2)
      val names by stringSet("test")
    }

    val bean = Bean()
    assertThat(bean.isEqualToDefault()).isTrue()
    testSerializer(
      expectedXml = "<Bean />",
      expectedJson = """{}""",
      bean = bean,
    )

    bean.names.clear()
    bean.intIncrementModificationCount()
    assertThat(bean.isEqualToDefault()).isFalse()
    testSerializer(
      expectedXml = """
        <Bean>
          <names />
        </Bean>
        """,
      expectedJson = """
        {
          "names": []
        }
      """.trimIndent(),
      bean = bean,
    )
  }

  @Tag("b")
  private class Bean4 {
    @CollectionBean
    val list = ArrayList<String>()
  }

  @Suppress("unused")
  private class BeanWithArrayWithoutTagName {
    @XCollection
    var foo = arrayOf("a")
  }
}
