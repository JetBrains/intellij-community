// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.configurationStore.xml

import com.intellij.configurationStore.clearBindingCache
import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.jdomSerializer
import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.serialization.SerializationException
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.assertConcurrent
import com.intellij.util.xmlb.*
import com.intellij.util.xmlb.annotations.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.junit.Test
import java.util.*

internal class XmlSerializerTest {
  @Test fun annotatedInternalVar() {
    @Suppress("PropertyName")
    class Bean {
      @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
      var PLACES_MAP = TreeMap<String, String>()
    }

    val data = Bean()
    data.PLACES_MAP.put("foo", "bar")
    testSerializer(
      expectedXml = """
        <Bean>
          <option name="PLACES_MAP">
            <entry key="foo" value="bar" />
          </option>
        </Bean>
      """,
      expectedJson = """
        {
          "places_map": {
            "foo": "foo"
          }
        }
      """,
      bean = data,
    )
  }

  @Test
  fun testClearBindingCache() {
    if (!UsefulTestCase.IS_UNDER_TEAMCITY) {
      clearBindingCache()
    }
  }

  @Test fun `no error if no accessors`() {
    class EmptyBean

    testSerializer("<EmptyBean />", EmptyBean(), expectedJson = """{}""")
  }

  @Test fun `suppress no accessors warn`() {
    @Property(assertIfNoBindings = false)
    class EmptyBean

    testSerializer("<EmptyBean />", EmptyBean())
  }

  @Test fun publicFieldSerialization() {
    val bean = BeanWithPublicFields()

    testSerializer(
      expectedXml = """
        <BeanWithPublicFields>
          <option name="INT_V" value="1" />
          <option name="STRING_V" value="hello" />
        </BeanWithPublicFields>
      """,
      expectedJson = """
        {
          "int_v": 1,
          "string_v": "hello"
        }
      """.trimIndent(),
      bean = bean)

    bean.INT_V = 2
    bean.STRING_V = "bye"

    testSerializer(
      expectedXml = """
        <BeanWithPublicFields>
          <option name="INT_V" value="2" />
          <option name="STRING_V" value="bye" />
        </BeanWithPublicFields>
      """,
      expectedJson = """
        {
          "int_v": 2,
          "string_v": "bye"
        }
      """.trimIndent(),
      bean = bean,
    )
  }

  @Test fun publicFieldSerializationWithInheritance() {
    val bean = BeanWithPublicFieldsDescendant()

    testSerializer("""
    <BeanWithPublicFieldsDescendant>
      <option name="NEW_S" value="foo" />
      <option name="INT_V" value="1" />
      <option name="STRING_V" value="hello" />
    </BeanWithPublicFieldsDescendant>""", bean)

    bean.INT_V = 2
    bean.STRING_V = "bye"
    bean.NEW_S = "bar"

    testSerializer("""<BeanWithPublicFieldsDescendant>
  <option name="NEW_S" value="bar" />
  <option name="INT_V" value="2" />
  <option name="STRING_V" value="bye" />
</BeanWithPublicFieldsDescendant>""", bean)
  }

  private class BeanWithSubBean {
    var bean1: BeanWithPublicFields? = BeanWithPublicFields()
    var bean2: BeanWithPublicFields? = BeanWithPublicFields()
  }

  @Test fun subBeanSerialization() {
    val bean = BeanWithSubBean()
    testSerializer("""<BeanWithSubBean>
  <option name="bean1">
    <BeanWithPublicFields>
      <option name="INT_V" value="1" />
      <option name="STRING_V" value="hello" />
    </BeanWithPublicFields>
  </option>
  <option name="bean2">
    <BeanWithPublicFields>
      <option name="INT_V" value="1" />
      <option name="STRING_V" value="hello" />
    </BeanWithPublicFields>
  </option>
</BeanWithSubBean>""", bean)
    bean.bean2!!.INT_V = 2
    bean.bean2!!.STRING_V = "bye"

    testSerializer("""<BeanWithSubBean>
  <option name="bean1">
    <BeanWithPublicFields>
      <option name="INT_V" value="1" />
      <option name="STRING_V" value="hello" />
    </BeanWithPublicFields>
  </option>
  <option name="bean2">
    <BeanWithPublicFields>
      <option name="INT_V" value="2" />
      <option name="STRING_V" value="bye" />
    </BeanWithPublicFields>
  </option>
</BeanWithSubBean>""", bean)
  }

  @Test fun subBeanSerializationAndSkipDefaults() {
    val bean = BeanWithSubBean()
    testSerializer("<BeanWithSubBean />", bean, SkipDefaultsSerializationFilter())
  }

  @Suppress("EqualsOrHashCode", "PropertyName")
  private class BeanWithEquals {
    var STRING_V = "hello"

    override fun equals(other: Any?): Boolean {
      // any instance of this class is equal
      return this === other || (other != null && javaClass == other.javaClass)
    }
  }

  @Test fun subBeanWithEqualsSerializationAndSkipDefaults() {
    @Tag("bean")
    class BeanWithSubBeanWithEquals {
      @Suppress("unused")
      var bean1: BeanWithPublicFields = BeanWithPublicFields()
      var bean2: BeanWithEquals = BeanWithEquals()
    }

    val bean = BeanWithSubBeanWithEquals()
    val filter = SkipDefaultsSerializationFilter()
    testSerializer("<bean />", bean, filter)

    bean.bean2.STRING_V = "new"
    testSerializer("<bean />", bean, filter)
  }

  @Test fun nullFieldValue() {
    val bean1 = BeanWithPublicFields()

    testSerializer("""<BeanWithPublicFields>
  <option name="INT_V" value="1" />
  <option name="STRING_V" value="hello" />
</BeanWithPublicFields>""", bean1)

    bean1.STRING_V = null

    testSerializer("""<BeanWithPublicFields>
  <option name="INT_V" value="1" />
  <option name="STRING_V" />
</BeanWithPublicFields>""", bean1)

    val bean2 = BeanWithSubBean()
    bean2.bean1 = null
    bean2.bean2 = null

    testSerializer("""<BeanWithSubBean>
  <option name="bean1" />
  <option name="bean2" />
</BeanWithSubBean>""", bean2)
  }

  @Suppress("PropertyName")
  private data class BeanWithOption(@OptionTag("path") var PATH: String? = null)

  @Test fun optionTag() {
    val bean = BeanWithOption()
    bean.PATH = "123"
    testSerializer("<BeanWithOption>\n" + "  <option name=\"path\" value=\"123\" />\n" + "</BeanWithOption>", bean)
  }

  @Suppress("PropertyName")
  private data class BeanWithCustomizedOption(@OptionTag(tag = "setting", nameAttribute = "key", valueAttribute = "saved") var PATH: String? = null)

  @Test fun customizedOptionTag() {
    val bean = BeanWithCustomizedOption()
    bean.PATH = "123"
    testSerializer(
      expectedXml = """
        <BeanWithCustomizedOption>
          <setting key="PATH" saved="123" />
        </BeanWithCustomizedOption>
      """,
      expectedJson = """{"path": "123"}""",
      bean = bean,
    )
  }

  @Test fun propertySerialization() {
    val bean = BeanWithProperty()
    testSerializer("<BeanWithProperty>\n" + "  <option name=\"name\" value=\"James\" />\n" + "</BeanWithProperty>", bean)
    bean.name = "Bond"
    testSerializer("<BeanWithProperty>\n" + "  <option name=\"name\" value=\"Bond\" />\n" + "</BeanWithProperty>", bean)
  }

  @Suppress("PropertyName")
  private class BeanWithFieldWithTagAnnotation {
    @Tag("name") var STRING_V = "hello"
  }

  @Test fun `parallel deserialization`() {
    val e = Element("root").addContent(Element("name").setText("x"))
    assertConcurrent(*Array(5) {
      {
        repeat(9) {
          val bean = deserialize<BeanWithFieldWithTagAnnotation>(e)
          assertThat(bean).isNotNull()
          assertThat(bean.STRING_V).isEqualTo("x")
        }
      }
    })
  }

  class Complex {
    var foo: Complex? = null
  }

  @Test fun `self class reference deserialization`() {
    testSerializer(
      expectedXml = """
        <Complex>
          <option name="foo" />
        </Complex>
      """,
      expectedJson = """
        {}
      """.trimIndent(),
      bean = Complex(),
    )
  }

  @Test fun fieldWithTagAnnotation() {
    val bean = BeanWithFieldWithTagAnnotation()
    testSerializer("<BeanWithFieldWithTagAnnotation>\n" + "  <name>hello</name>\n" + "</BeanWithFieldWithTagAnnotation>", bean)
    bean.STRING_V = "bye"
    testSerializer("<BeanWithFieldWithTagAnnotation>\n" + "  <name>bye</name>\n" + "</BeanWithFieldWithTagAnnotation>", bean)
  }

  @Test fun escapeCharsInTagText() {
    val bean = BeanWithFieldWithTagAnnotation()
    bean.STRING_V = "a\nb\"<"

    testSerializer("<BeanWithFieldWithTagAnnotation>\n" + "  <name>a\nb&quot;&lt;</name>\n" + "</BeanWithFieldWithTagAnnotation>", bean)
  }

  @Test fun escapeCharsInAttributeValue() {
    val bean = BeanWithPropertiesBoundToAttribute()
    bean.name = "a\nb\"<"
    testSerializer("<BeanWithPropertiesBoundToAttribute count=\"3\" name=\"a&#10;b&quot;&lt;\" />", bean)
  }

  @Test fun shuffledDeserialize() {
    var bean = BeanWithPublicFields()
    bean.INT_V = 987
    bean.STRING_V = "1234"

    val element = serialize(bean)!!

    val node = element.children[0]
    element.removeContent(node)
    element.addContent(node)

    bean = deserialize(element)
    assertThat(bean.INT_V).isEqualTo(987)
    assertThat(bean.STRING_V).isEqualTo("1234")
  }

  @Test fun filterSerializer() {
    val bean = BeanWithPublicFields()
    assertSerializer(bean, "<BeanWithPublicFields>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "</BeanWithPublicFields>", SerializationFilter { accessor, _ -> accessor.name.startsWith("I") })
  }

  @Test fun transient() {
    @Suppress("unused", "PropertyName")
    class Bean {
      var INT_V: Int = 1
        @Transient
        get

      @Transient
      fun getValue(): String = "foo"

      var foo: String? = null
    }

    testSerializer("""<Bean>
  <option name="foo" />
</Bean>""", Bean())
  }

  @Test fun propertyWithoutTagWithPrimitiveType() {
    @Suppress("PropertyName")
    class BeanWithPropertyWithoutTagOnPrimitiveValue {
      @Suppress("unused")
      @Property(surroundWithTag = false)
      var INT_V = 1
    }

    val bean = BeanWithPropertyWithoutTagOnPrimitiveValue()
    assertThatThrownBy {
      testSerializer("<BeanWithPropertyWithoutTagOnPrimitiveValue><name>hello</name></BeanWithPropertyWithoutTagOnPrimitiveValue>", bean)
    }.isInstanceOf(SerializationException::class.java)
  }

  @Test fun propertyWithoutTag() {
    @Suppress("PropertyName")
    @Tag("bean")
    class BeanWithPropertyWithoutTag {
      @Property(surroundWithTag = false)
      var BEAN1 = BeanWithPublicFields()
      var INT_V = 1
    }

    val bean = BeanWithPropertyWithoutTag()

    testSerializer(
      expectedXml = """
        <bean>
        <option name="INT_V" value="1" />
        <BeanWithPublicFields>
          <option name="INT_V" value="1" />
          <option name="STRING_V" value="hello" />
        </BeanWithPublicFields>
      </bean>
      """,
      expectedJson = """
        {
          "int_v": 1,
          "bean1": {
            "int_v": 1,
            "string_v": "hello"
          }
        }
      """.trimIndent(),
      bean = bean,
    )

    bean.INT_V = 2
    bean.BEAN1.STRING_V = "junk"

    testSerializer("""<bean>
  <option name="INT_V" value="2" />
  <BeanWithPublicFields>
    <option name="INT_V" value="1" />
    <option name="STRING_V" value="junk" />
  </BeanWithPublicFields>
</bean>""", bean)
  }

  @Tag("bean")
  private class BeanWithArrayWithoutAllTag {
    @Property(surroundWithTag = false)
    @XCollection(elementName = "vValue", valueAttributeName = "v")
    var v = arrayOf("a", "b")

    var intV = 1
  }

  @Test fun arrayWithoutAllTags() {
    val bean = BeanWithArrayWithoutAllTag()

    testSerializer("""<bean>
  <option name="intV" value="1" />
  <vValue v="a" />
  <vValue v="b" />
</bean>""", bean)

    bean.intV = 2
    bean.v = arrayOf("1", "2", "3")

    testSerializer("""<bean>
  <option name="intV" value="2" />
  <vValue v="1" />
  <vValue v="2" />
  <vValue v="3" />
</bean>""", bean)
  }

  @Test fun arrayWithoutAllTags2() {
    @Tag("bean")
    class BeanWithArrayWithoutAllTag2 {
      @Property(surroundWithTag = false)
      @XCollection(elementName = "vValue", valueAttributeName = "")
      var v = arrayOf("a", "b")
      var intV = 1
    }

    val bean = BeanWithArrayWithoutAllTag2()

    testSerializer(
      expectedXml = """
        <bean>
          <option name="intV" value="1" />
          <vValue>a</vValue>
          <vValue>b</vValue>
        </bean>
      """,
      expectedJson = """
        {
          "intV": 1,
          "v": [
            "a",
            "b"
          ]
        }
      """,
      bean = bean,
    )

    bean.intV = 2
    bean.v = arrayOf("1", "2", "3")

    testSerializer("""<bean>
  <option name="intV" value="2" />
  <vValue>1</vValue>
  <vValue>2</vValue>
  <vValue>3</vValue>
</bean>""", bean)
  }

  @Test fun deserializeFromFormattedXML() {
    val bean = deserialize<BeanWithArrayWithoutAllTag>(JDOMUtil.load("""
        <bean>
        <option name="intV" value="2"/>
        <vValue v="1"/>
        <vValue v="2"/>
        <vValue v="3"/>
      </bean>"""))
    assertThat(bean.intV).isEqualTo(2)
    assertThat("[1, 2, 3]").isEqualTo(listOf(*bean.v).toString())
  }

  @Suppress("PropertyName")
  private class BeanWithPropertiesBoundToAttribute {
    @Attribute("count")
    var COUNT = 3
    @Attribute("name")
    var name = "James"
    @Suppress("unused")
    @Attribute("occupation")
    var occupation: String? = null
  }

  @Test fun beanWithPrimitivePropertyBoundToAttribute() {
    val bean = BeanWithPropertiesBoundToAttribute()

    testSerializer(
      expectedXml = """<BeanWithPropertiesBoundToAttribute count="3" name="James" />""",
      bean = bean,
      expectedJson = """
        {
          "count": 3,
          "name": "James"
        }
      """)

    bean.COUNT = 10
    bean.name = "Bond"

    testSerializer(
      expectedXml = """<BeanWithPropertiesBoundToAttribute count="10" name="Bond" />""",
      expectedJson = """
        {
          "count": 10,
          "name": "Bond"
        }
      """,
      bean = bean,
    )
  }

  @Test fun propertyFilter() {
    class PropertyFilterTest : SerializationFilter {
      override fun accepts(accessor: Accessor, bean: Any): Boolean {
        val v = accessor.readUnsafe(bean)
        return v != "skip" && v != null
      }
    }

    @Suppress("PropertyName")
    class BeanWithPropertyFilter {
      @Property(filter = PropertyFilterTest::class) var STRING_V: String? = null
    }

    val bean = BeanWithPropertyFilter()
    bean.STRING_V = "hello"

    testSerializer(
      expectedXml = """
        <BeanWithPropertyFilter>
          <option name="STRING_V" value="hello" />
        </BeanWithPropertyFilter>
      """,
      expectedJson = """
        {
          "string_v": "hello"
        }
      """,
      bean = bean,
    )

    bean.STRING_V = "bye"

    testSerializer(
      expectedXml = """
        <BeanWithPropertyFilter>
          <option name="STRING_V" value="bye" />
        </BeanWithPropertyFilter>
      """,
      expectedJson = """
        {
          "string_v": "bye"
        }
      """,
      bean = bean,
    )

    bean.STRING_V = "skip"

    testSerializer(
      expectedXml = """
        <BeanWithPropertyFilter />
      """,
      expectedJson = """{}""",
      bean = bean,
    )
  }

  @Suppress("PropertyName")
  private class BeanWithJDOMElement {
    var STRING_V: String = "hello"
    @Tag("actions") var actions: Element? = null
  }

  @Test fun serializeJDOMElementField() {
    val element = BeanWithJDOMElement()
    element.STRING_V = "a"
    element.actions = Element("x").addContent(Element("a")).addContent(Element("b"))
    assertSerializer(element, """<BeanWithJDOMElement>
  <option name="STRING_V" value="a" />
  <actions>
    <a />
    <b />
  </actions>
</BeanWithJDOMElement>""", null)

    element.actions = null
    assertSerializer(element, """<BeanWithJDOMElement>
  <option name="STRING_V" value="a" />
</BeanWithJDOMElement>""", null)
  }

  @Test fun deserializeJDOMElementField() {
    val bean = deserialize<BeanWithJDOMElement>(JDOMUtil.load(
      "<BeanWithJDOMElement><option name=\"STRING_V\" value=\"bye\"/><actions><action/><action/></actions></BeanWithJDOMElement>"))

    assertThat(bean.STRING_V).isEqualTo("bye")
    assertThat(bean.actions).isNotNull
    assertThat(bean.actions!!.getChildren("action")).hasSize(2)
  }

  @Suppress("PropertyName")
  class BeanWithJDOMElementArray {
    var STRING_V: String = "hello"
    @Tag("actions") var actions: Array<Element>? = null
  }

  @Test fun jdomElementArrayField() {
    val text = "<BeanWithJDOMElementArray>\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "  <actions>\n" + "    <action />\n" + "    <action />\n" + "  </actions>\n" + "  <actions>\n" + "    <action />\n" + "  </actions>\n" + "</BeanWithJDOMElementArray>"
    val bean = deserialize<BeanWithJDOMElementArray>(JDOMUtil.load(text))

    assertThat(bean.STRING_V).isEqualTo("bye")
    assertThat(bean.actions).isNotNull().hasSize(2)
    assertThat(bean.actions!![0].children).hasSize(2)
    assertThat(bean.actions!![1].children).hasSize(1)

    assertSerializer(bean, text, null)

    bean.actions = null
    val newText = "<BeanWithJDOMElementArray>\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "</BeanWithJDOMElementArray>"
    testSerializer(newText, bean)

    bean.actions = emptyArray()
    testSerializer(newText, bean)
  }

  @Test fun textAnnotation() {
    val bean = BeanWithTextAnnotation()

    testSerializer(
      expectedXml = """
        <BeanWithTextAnnotation>
          <option name="INT_V" value="1" />
          hello
        </BeanWithTextAnnotation>
      """,
      expectedJson = """
        {
          "int_v": 1,
          "string_v": "hello"
        }
      """,
      bean = bean,
    )

    bean.INT_V = 2
    bean.STRING_V = "bye"

    testSerializer(
      expectedXml = """
        <BeanWithTextAnnotation>
          <option name="INT_V" value="2" />
          bye
        </BeanWithTextAnnotation>
      """,
      bean = bean,
    )
  }

  @Suppress("PropertyName")
  private class BeanWithEnum {
    @Suppress("unused")
    enum class TestEnum { VALUE_1, VALUE_2, VALUE_3 }

    var FLD = TestEnum.VALUE_1
  }

  @Test fun enums() {
    val bean = BeanWithEnum()

    testSerializer("<BeanWithEnum>\n" + "  <option name=\"FLD\" value=\"VALUE_1\" />\n" + "</BeanWithEnum>", bean)

    bean.FLD = BeanWithEnum.TestEnum.VALUE_3

    testSerializer("<BeanWithEnum>\n" + "  <option name=\"FLD\" value=\"VALUE_3\" />\n" + "</BeanWithEnum>", bean)
  }

  @Tag("condition")
  private class ConditionBean {
    @Attribute("expression")
    var newCondition: String? = null
    @Text
    var oldCondition: String? = null
  }

  @Test fun conversionFromTextToAttribute() {
    @Tag("bean")
    class Bean {
      @Property(surroundWithTag = false)
      var conditionBean = ConditionBean()
    }

    var bean = Bean()
    bean.conditionBean.oldCondition = "2+2"
    testSerializer("<bean>\n  <condition>2+2</condition>\n</bean>", bean)

    bean = Bean()
    bean.conditionBean.newCondition = "2+2"
    testSerializer("<bean>\n  <condition expression=\"2+2\" />\n" + "</bean>", bean)
  }

  @Test fun `no wrap`() {
    @Tag("bean")
    class Bean {
      @Property(flat = true)
      var conditionBean = ConditionBean()
    }

    var bean = Bean()
    bean.conditionBean.oldCondition = "2+2"
    testSerializer("<bean>2+2</bean>", bean)

    bean = Bean()
    bean.conditionBean.newCondition = "2+2"
    testSerializer("<bean expression=\"2+2\" />", bean)
  }

  @Test fun deserializeInto() {
    val bean = BeanWithPublicFields()
    bean.STRING_V = "zzz"

    XmlSerializer.deserializeInto(bean,
                                  JDOMUtil.load("<BeanWithPublicFields><option name=\"INT_V\" value=\"999\"/></BeanWithPublicFields>"))

    assertThat(bean.INT_V).isEqualTo(999)
    assertThat(bean.STRING_V).isEqualTo("zzz")
  }

  @Test fun defaultAttributeName() {
    class BeanWithDefaultAttributeName {
      @Suppress("unused")
      @Attribute fun getFoo() = "foo"

      @Suppress("unused")
      fun setFoo(@Suppress("UNUSED_PARAMETER") value: String) {
      }
    }

    testSerializer("<BeanWithDefaultAttributeName foo=\"foo\" />", BeanWithDefaultAttributeName())
  }

  @Test
  fun ordered() {
    @Tag("bean")
    class Bean {
      @Attribute
      var ab: String? = null

      @Attribute
      var module: String? = null

      @Suppress("unused")
      @Attribute
      var ac: String? = null
    }

    val bean = Bean()
    bean.module = "module"
    bean.ab = "ab"
    testSerializer(
      expectedXml = """<bean ab="ab" module="module" />""",
      expectedJson = """
        {
          "ab": "ab",
          "module": "module"
        }
      """,
      bean = bean,
      filter = SkipDefaultsSerializationFilter(),
    )
  }

  @Test
  fun cdataAfterNewLine() {
    @Tag("bean")
    data class Bean(@Tag val description: String? = null)

    var bean = deserialize<Bean>(JDOMUtil.load("""<bean>
      <description>
        <![CDATA[
        <h4>Node.js integration</h4>
        ]]>
      </description>
    </bean>"""))
    assertThat(bean.description).isEqualToIgnoringWhitespace("<h4>Node.js integration</h4>")

    bean = deserialize(JDOMUtil.load("""<bean><description><![CDATA[<h4>Node.js integration</h4>]]></description></bean>"""))
    assertThat(bean.description).isEqualTo("<h4>Node.js integration</h4>")
  }

  @Test
  fun `option tag for bean and empty value attribute to serialize into`() {
    @Tag("subBean")
    data class SubBean(@Tag val description: String? = null)

    @Tag("bean")
    class Bean(
      @OptionTag(value = "selected-file", nameAttribute = "id", tag = "todo-panel", valueAttribute = "")
      val sub: SubBean? = null,
    )

    testSerializer("""
      <bean>
        <todo-panel id="selected-file">
          <description>hello</description>
        </todo-panel>
      </bean>
    """.trimIndent(), Bean(sub = SubBean(description = "hello")))
  }

  @Test
  fun nullCollection() {
    @Suppress("unused")
    class BeanWithCollection {
      @Property(surroundWithTag = false)
      @XCollection
      var collection: List<BeanWithProperty>? = null
    }
    testSerializer("<BeanWithCollection />", BeanWithCollection())
  }
}

internal fun assertSerializer(bean: Any, expected: String, filter: SerializationFilter? = null, description: String = "Serialization failure"): Element {
  val element = jdomSerializer.serialize(bean = bean, filter = filter, createElementIfEmpty = true)!!
  com.intellij.testFramework.assertions.Assertions.assertThat(element).`as`(description).isEqualTo(expected)
  return element
}

fun <T : Any> testSerializer(
  @Language("XML") expectedXml: String,
  bean: T,
  filter: SerializationFilter? = null,
  @Language("JSON") expectedJson: String? = null,
): T {
  val expectedTrimmed = expectedXml.trimIndent()
  val element = assertSerializer(bean = bean, expected = expectedTrimmed, filter = filter)

  // test deserializer
  val o = jdomSerializer.deserialize(element, bean.javaClass, JdomAdapter)
  assertSerializer(bean = o, expected = expectedTrimmed, filter = filter, description = "Deserialization failure")

  if (expectedJson != null) {
    val binding = jdomSerializer.getBeanBinding(bean.javaClass)
    val jsonTree = binding.toJson(bean, filter)
    val expectedTree = PrettyJson.parseToJsonElement(expectedJson)
    val expectedNormalizedJson = PrettyJson.encodeToString(expectedTree)
    assertThat(PrettyJson.encodeToString(jsonTree)).isEqualTo(expectedNormalizedJson)

    val deserializedBean = binding.fromJson(currentValue = null, element = expectedTree)!!
    assertThat(PrettyJson.encodeToString(binding.toJson(deserializedBean, filter))).isEqualTo(expectedNormalizedJson)
  }
  return o
}

@Suppress("PropertyName")
internal open class BeanWithPublicFields(@JvmField var INT_V: Int = 1, @JvmField var STRING_V: String? = "hello") : Comparable<BeanWithPublicFields> {
  override fun compareTo(other: BeanWithPublicFields) = StringUtil.compare(STRING_V, other.STRING_V, false)
}

@Suppress("PropertyName")
internal class BeanWithTextAnnotation {
  var INT_V: Int = 1
  @Text var STRING_V: String = "hello"

  constructor(intV: Int, stringV: String) {
    INT_V = intV
    STRING_V = stringV
  }

  constructor()
}

internal class BeanWithProperty {
  var name: String = "James"

  constructor()

  constructor(name: String) {
    this.name = name
  }
}

@Suppress("PropertyName")
internal class BeanWithPublicFieldsDescendant(@JvmField var NEW_S: String? = "foo") : BeanWithPublicFields()

@OptIn(ExperimentalSerializationApi::class)
private val PrettyJson = Json {
  prettyPrint = true
  prettyPrintIndent = "  "
  ignoreUnknownKeys = true
}
