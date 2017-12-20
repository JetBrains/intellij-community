/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore.xml

import com.intellij.configurationStore.StoredPropertyStateTest
import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.assertConcurrent
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.loadElement
import com.intellij.util.xmlb.*
import com.intellij.util.xmlb.annotations.*
import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.jdom.Element
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Suite
import java.util.*

@RunWith(Suite::class)
@Suite.SuiteClasses(
  XmlSerializerTest::class,
  XmlSerializerMapTest::class,
  XmlSerializerCollectionTest::class,
  StoredPropertyStateTest::class,
  KotlinXmlSerializerTest::class,
  XmlSerializerConversionTest::class
)
class XmlSerializerTestSuite

internal class XmlSerializerTest {
  @Test fun annotatedInternalVar() {
    class Bean {
      @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
      internal var PLACES_MAP = TreeMap<String, String>()
    }

    val data = Bean()
    data.PLACES_MAP.put("foo", "bar")
    testSerializer("""
    <Bean>
      <option name="PLACES_MAP">
        <entry key="foo" value="bar" />
      </option>
    </Bean>""", data)
  }

  @Test fun emptyBeanSerialization() {
    class EmptyBean

    testSerializer("<EmptyBean />", EmptyBean())
  }

  @Tag("Bean")
  private class EmptyBeanWithCustomName

  @Test fun emptyBeanSerializationWithCustomName() {
    testSerializer("<Bean />", EmptyBeanWithCustomName())
  }

  @Test fun publicFieldSerialization() {
    val bean = BeanWithPublicFields()

    testSerializer("<BeanWithPublicFields>\n  <option name=\"INT_V\" value=\"1\" />\n  <option name=\"STRING_V\" value=\"hello\" />\n</BeanWithPublicFields>", bean)

    bean.INT_V = 2
    bean.STRING_V = "bye"

    testSerializer("<BeanWithPublicFields>\n  <option name=\"INT_V\" value=\"2\" />\n  <option name=\"STRING_V\" value=\"bye\" />\n</BeanWithPublicFields>", bean)
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
    var BEAN1: EmptyBeanWithCustomName? = EmptyBeanWithCustomName()
    var BEAN2: BeanWithPublicFields? = BeanWithPublicFields()
  }

  @Test fun subBeanSerialization() {
    val bean = BeanWithSubBean()
    testSerializer("<BeanWithSubBean>\n" + "  <option name=\"BEAN1\">\n" + "    <Bean />\n" + "  </option>\n" + "  <option name=\"BEAN2\">\n" + "    <BeanWithPublicFields>\n" + "      <option name=\"INT_V\" value=\"1\" />\n" + "      <option name=\"STRING_V\" value=\"hello\" />\n" + "    </BeanWithPublicFields>\n" + "  </option>\n" + "</BeanWithSubBean>", bean)
    bean.BEAN2!!.INT_V = 2
    bean.BEAN2!!.STRING_V = "bye"

    testSerializer("<BeanWithSubBean>\n" + "  <option name=\"BEAN1\">\n" + "    <Bean />\n" + "  </option>\n" + "  <option name=\"BEAN2\">\n" + "    <BeanWithPublicFields>\n" + "      <option name=\"INT_V\" value=\"2\" />\n" + "      <option name=\"STRING_V\" value=\"bye\" />\n" + "    </BeanWithPublicFields>\n" + "  </option>\n" + "</BeanWithSubBean>", bean)
  }

  @Test fun SubBeanSerializationAndSkipDefaults() {
    val bean = BeanWithSubBean()
    testSerializer("<BeanWithSubBean />", bean, SkipDefaultsSerializationFilter())
  }

  @Suppress("EqualsOrHashCode")
  private class BeanWithEquals {
    var STRING_V = "hello"

    override fun equals(other: Any?): Boolean {
      // any instance of this class is equal
      return this === other || (other != null && javaClass == other.javaClass)
    }
  }

  private class BeanWithSubBeanWithEquals {
    var BEAN1: EmptyBeanWithCustomName = EmptyBeanWithCustomName()
    var BEAN2: BeanWithEquals = BeanWithEquals()
  }

  @Test fun subBeanWithEqualsSerializationAndSkipDefaults() {
    val bean = BeanWithSubBeanWithEquals()
    val filter = SkipDefaultsSerializationFilter()
    testSerializer("<BeanWithSubBeanWithEquals />", bean, filter)

    bean.BEAN2.STRING_V = "new"
    testSerializer("<BeanWithSubBeanWithEquals />", bean, filter)
  }

  @Test fun nullFieldValue() {
    val bean1 = BeanWithPublicFields()

    testSerializer("<BeanWithPublicFields>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "  <option name=\"STRING_V\" value=\"hello\" />\n" + "</BeanWithPublicFields>", bean1)

    bean1.STRING_V = null

    testSerializer("<BeanWithPublicFields>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "  <option name=\"STRING_V\" />\n" + "</BeanWithPublicFields>", bean1)

    val bean2 = BeanWithSubBean()
    bean2.BEAN1 = null
    bean2.BEAN2 = null

    testSerializer("<BeanWithSubBean>\n" + "  <option name=\"BEAN1\" />\n" + "  <option name=\"BEAN2\" />\n" + "</BeanWithSubBean>", bean2)
  }

  private data class BeanWithOption(@OptionTag("path") var PATH: String? = null)

  @Test fun optionTag() {
    val bean = BeanWithOption()
    bean.PATH = "123"
    testSerializer("<BeanWithOption>\n" + "  <option name=\"path\" value=\"123\" />\n" + "</BeanWithOption>", bean)
  }

  private data class BeanWithCustomizedOption(@OptionTag(tag = "setting", nameAttribute = "key", valueAttribute = "saved") var PATH: String? = null)

  @Test fun customizedOptionTag() {
    val bean = BeanWithCustomizedOption()
    bean.PATH = "123"
    testSerializer("<BeanWithCustomizedOption>\n" + "  <setting key=\"PATH\" saved=\"123\" />\n" + "</BeanWithCustomizedOption>", bean)
  }

  @Test fun propertySerialization() {
    val bean = BeanWithProperty()
    testSerializer("<BeanWithProperty>\n" + "  <option name=\"name\" value=\"James\" />\n" + "</BeanWithProperty>", bean)
    bean.name = "Bond"
    testSerializer("<BeanWithProperty>\n" + "  <option name=\"name\" value=\"Bond\" />\n" + "</BeanWithProperty>", bean)
  }

  private class BeanWithFieldWithTagAnnotation {
    @Tag("name") var STRING_V = "hello"
  }

  @Test fun `parallel deserialization`() {
    val e = Element("root").addContent(Element("name").setText("x"))
    assertConcurrent(*Array(5) {
      {
        for (i in 0..9) {
          val bean = e.deserialize<BeanWithFieldWithTagAnnotation>()
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
    testSerializer("""
    <Complex>
      <option name="foo" />
    </Complex>""", Complex())
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

    val element = bean.serialize()!!

    val node = element.children.get(0)
    element.removeContent(node)
    element.addContent(node)

    bean = element.deserialize<BeanWithPublicFields>()
    assertThat(bean.INT_V).isEqualTo(987)
    assertThat(bean.STRING_V).isEqualTo("1234")
  }

  @Test fun filterSerializer() {
    val bean = BeanWithPublicFields()
    assertSerializer(bean, "<BeanWithPublicFields>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "</BeanWithPublicFields>", SerializationFilter { accessor, _ -> accessor.name.startsWith("I") })
  }

  @Test fun transient() {
    class Bean {
      var INT_V: Int = 1
        @Transient
        get

      @Transient fun getValue(): String = "foo"
    }

    testSerializer("<Bean />", Bean())
  }

  @Test fun PropertyWithoutTagWithPrimitiveType() {
    class BeanWithPropertyWithoutTagOnPrimitiveValue {
      @Property(surroundWithTag = false)
      var INT_V = 1
    }

    val bean = BeanWithPropertyWithoutTagOnPrimitiveValue()
    try {
      testSerializer("<BeanWithPropertyWithoutTagOnPrimitiveValue><name>hello</name></BeanWithPropertyWithoutTagOnPrimitiveValue>", bean)
    }
    catch (e: XmlSerializationException) {
      return
    }

    TestCase.fail("No Exception")
  }

  @Test fun propertyWithoutTag() {
    @Tag("bean")
    class BeanWithPropertyWithoutTag {
      @Property(surroundWithTag = false)
      var BEAN1 = BeanWithPublicFields()
      var INT_V = 1
    }

    val bean = BeanWithPropertyWithoutTag()

    testSerializer("""<bean>
  <option name="INT_V" value="1" />
  <BeanWithPublicFields>
    <option name="INT_V" value="1" />
    <option name="STRING_V" value="hello" />
  </BeanWithPublicFields>
</bean>""", bean)

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
    @XCollection(elementName = "vvalue", valueAttributeName = "v")
    var v = arrayOf("a", "b")

    var intV = 1
  }

  @Test fun arrayWithoutAllTags() {
    val bean = BeanWithArrayWithoutAllTag()

    testSerializer("""<bean>
  <option name="intV" value="1" />
  <vvalue v="a" />
  <vvalue v="b" />
</bean>""", bean)

    bean.intV = 2
    bean.v = arrayOf("1", "2", "3")

    testSerializer("""<bean>
  <option name="intV" value="2" />
  <vvalue v="1" />
  <vvalue v="2" />
  <vvalue v="3" />
</bean>""", bean)
  }

  @Test fun arrayWithoutAllTags2() {
    @Tag("bean")
    class BeanWithArrayWithoutAllTag2 {
      @Property(surroundWithTag = false)
      @XCollection(elementName = "vvalue", valueAttributeName = "")
      var v = arrayOf("a", "b")
      var intV = 1
    }

    val bean = BeanWithArrayWithoutAllTag2()

    testSerializer("""<bean>
  <option name="intV" value="1" />
  <vvalue>a</vvalue>
  <vvalue>b</vvalue>
</bean>""", bean)

    bean.intV = 2
    bean.v = arrayOf("1", "2", "3")

    testSerializer("""<bean>
  <option name="intV" value="2" />
  <vvalue>1</vvalue>
  <vvalue>2</vvalue>
  <vvalue>3</vvalue>
</bean>""", bean)
  }

  @Test fun deserializeFromFormattedXML() {
    val bean = loadElement("<bean>\n" + "  <option name=\"intV\" value=\"2\"/>\n" + "  <vvalue v=\"1\"/>\n" + "  <vvalue v=\"2\"/>\n" + "  <vvalue v=\"3\"/>\n" + "</bean>").deserialize<BeanWithArrayWithoutAllTag>()
    assertThat(bean.intV).isEqualTo(2)
    assertThat("[1, 2, 3]").isEqualTo(Arrays.asList(*bean.v).toString())
  }

  private class BeanWithPropertiesBoundToAttribute {
    @Attribute("count")
    var COUNT = 3
    @Attribute("name")
    var name = "James"
    @Attribute("occupation")
    var occupation: String? = null
  }

  @Test fun BeanWithPrimitivePropertyBoundToAttribute() {
    val bean = BeanWithPropertiesBoundToAttribute()

    testSerializer("<BeanWithPropertiesBoundToAttribute count=\"3\" name=\"James\" />", bean)

    bean.COUNT = 10
    bean.name = "Bond"

    testSerializer("<BeanWithPropertiesBoundToAttribute count=\"10\" name=\"Bond\" />", bean)
  }


  private class BeanWithPropertyFilter {
    @Property(filter = PropertyFilterTest::class) var STRING_V: String = "hello"
  }

  private class PropertyFilterTest : SerializationFilter {
    override fun accepts(accessor: Accessor, bean: Any): Boolean {
      return accessor.read(bean) != "skip"
    }
  }

  @Test fun propertyFilter() {
    val bean = BeanWithPropertyFilter()

    testSerializer("<BeanWithPropertyFilter>\n" + "  <option name=\"STRING_V\" value=\"hello\" />\n" + "</BeanWithPropertyFilter>", bean)

    bean.STRING_V = "bye"

    testSerializer("<BeanWithPropertyFilter>\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "</BeanWithPropertyFilter>", bean)

    bean.STRING_V = "skip"

    assertSerializer(bean, "<BeanWithPropertyFilter />", null)
  }

  private class BeanWithJDOMElement {
    var STRING_V: String = "hello"
    @Tag("actions") var actions: Element? = null
  }

  @Test fun serializeJDOMElementField() {
    val element = BeanWithJDOMElement()
    element.STRING_V = "a"
    element.actions = Element("x").addContent(Element("a")).addContent(Element("b"))
    assertSerializer(element, "<BeanWithJDOMElement>\n" + "  <option name=\"STRING_V\" value=\"a\" />\n" + "  <actions>\n" + "    <a />\n" + "    <b />\n" + "  </actions>\n" + "</BeanWithJDOMElement>", null)

    element.actions = null
    assertSerializer(element, "<BeanWithJDOMElement>\n" + "  <option name=\"STRING_V\" value=\"a\" />\n" + "</BeanWithJDOMElement>", null)
  }

  @Test fun deserializeJDOMElementField() {
    val bean = loadElement("<BeanWithJDOMElement><option name=\"STRING_V\" value=\"bye\"/><actions><action/><action/></actions></BeanWithJDOMElement>").deserialize<BeanWithJDOMElement>()

    assertThat(bean.STRING_V).isEqualTo("bye")
    assertThat(bean.actions).isNotNull()
    assertThat(bean.actions!!.getChildren("action")).hasSize(2)
  }

  class BeanWithJDOMElementArray {
    var STRING_V: String = "hello"
    @Tag("actions") var actions: Array<Element>? = null
  }

  @Test fun jdomElementArrayField() {
    val text = "<BeanWithJDOMElementArray>\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "  <actions>\n" + "    <action />\n" + "    <action />\n" + "  </actions>\n" + "  <actions>\n" + "    <action />\n" + "  </actions>\n" + "</BeanWithJDOMElementArray>"
    val bean = loadElement(text).deserialize<BeanWithJDOMElementArray>()

    TestCase.assertEquals("bye", bean.STRING_V)
    TestCase.assertNotNull(bean.actions)
    TestCase.assertEquals(2, bean.actions!!.size)
    TestCase.assertEquals(2, bean.actions!![0].children.size)
    TestCase.assertEquals(1, bean.actions!![1].children.size)

    assertSerializer(bean, text, null)

    bean.actions = null
    val newText = "<BeanWithJDOMElementArray>\n" + "  <option name=\"STRING_V\" value=\"bye\" />\n" + "</BeanWithJDOMElementArray>"
    testSerializer(newText, bean)

    bean.actions = emptyArray()
    testSerializer(newText, bean)
  }

  @Test fun textAnnotation() {
    val bean = BeanWithTextAnnotation()

    testSerializer("<BeanWithTextAnnotation>\n" + "  <option name=\"INT_V\" value=\"1\" />\n" + "  hello\n" + "</BeanWithTextAnnotation>", bean)

    bean.INT_V = 2
    bean.STRING_V = "bye"

    testSerializer("<BeanWithTextAnnotation>\n" + "  <option name=\"INT_V\" value=\"2\" />\n" + "  bye\n" + "</BeanWithTextAnnotation>", bean)
  }

  private class BeanWithEnum {
    enum class TestEnum {
      VALUE_1,
      VALUE_2,
      VALUE_3
    }

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

  @Test fun `no_wrap`() {
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

    XmlSerializer.deserializeInto(bean, loadElement("<BeanWithPublicFields><option name=\"INT_V\" value=\"999\"/></BeanWithPublicFields>"))

    assertThat(bean.INT_V).isEqualTo(999)
    assertThat(bean.STRING_V).isEqualTo("zzz")
  }

  @Test fun defaultAttributeName() {
    class BeanWithDefaultAttributeName {
      @Attribute fun getFoo() = "foo"

      fun setFoo(@Suppress("UNUSED_PARAMETER") value: String) {
      }
    }

    testSerializer("<BeanWithDefaultAttributeName foo=\"foo\" />", BeanWithDefaultAttributeName())
  }

  private class Bean2 {
    @Attribute
    var ab: String? = null

    @Attribute
    var module: String? = null

    @Attribute
    var ac: String? = null
  }

  @Test fun ordered() {
    val bean = Bean2()
    bean.module = "module"
    bean.ab = "ab"
    testSerializer("<Bean2 ab=\"ab\" module=\"module\" />", bean, SkipDefaultsSerializationFilter())
  }

  @Test fun cdataAfterNewLine() {
    @Tag("bean")
    data class Bean(@Tag var description: String? = null)

    var bean = loadElement("""<bean>
  <description>
    <![CDATA[
    <h4>Node.js integration</h4>
    ]]>
  </description>
</bean>""").deserialize<Bean>()
    assertThat(bean.description).isEqualToIgnoringWhitespace("<h4>Node.js integration</h4>")

    bean = loadElement("""<bean><description><![CDATA[<h4>Node.js integration</h4>]]></description></bean>""").deserialize<Bean>()
    assertThat(bean.description).isEqualTo("<h4>Node.js integration</h4>")
  }
}

internal fun assertSerializer(bean: Any, expected: String, filter: SerializationFilter?, description: String = "Serialization failure"): Element {
  val element = bean.serialize(filter, createElementIfEmpty = true)!!
  assertThat(element).`as`(description).isEqualTo(expected)
  return element
}

internal fun <T: Any> testSerializer(@Language("XML") expectedText: String, bean: T, filter: SerializationFilter? = null): T {
  val expectedTrimmed = expectedText.trimIndent()
  val element = assertSerializer(bean, expectedTrimmed, filter)

  // test deserializer
  val o = element.deserialize(bean.javaClass)
  assertSerializer(o, expectedTrimmed, filter, "Deserialization failure")
  return o
}

internal open class BeanWithPublicFields(@JvmField var INT_V: Int = 1, @JvmField var STRING_V: String? = "hello") : Comparable<BeanWithPublicFields> {
  override fun compareTo(other: BeanWithPublicFields) = StringUtil.compare(STRING_V, other.STRING_V, false)
}

internal class BeanWithTextAnnotation {
  var INT_V: Int = 1
  @Text var STRING_V: String = "hello"

  constructor(INT_V: Int, STRING_V: String) {
    this.INT_V = INT_V
    this.STRING_V = STRING_V
  }

  constructor() {
  }
}

internal class BeanWithProperty {
  var name: String = "James"

  constructor() {
  }

  constructor(name: String) {
    this.name = name
  }
}

internal class BeanWithPublicFieldsDescendant(@JvmField var NEW_S: String? = "foo") : BeanWithPublicFields()