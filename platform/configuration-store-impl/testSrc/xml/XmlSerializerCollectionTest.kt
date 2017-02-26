@file:Suppress("DEPRECATION")

package com.intellij.configurationStore.xml

import com.intellij.configurationStore.deserialize
import com.intellij.openapi.util.JDOMExternalizableStringList
import com.intellij.util.SmartList
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.XmlSerializationException
import com.intellij.util.xmlb.annotations.AbstractCollection
import com.intellij.util.xmlb.annotations.CollectionBean
import com.intellij.util.xmlb.annotations.Tag
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jdom.Element
import org.junit.Test
import java.util.*

internal class XmlSerializerCollectionTest {
  @Test fun testJDOMExternalizableStringList() {
    val bean = Bean3()
    bean.list.add("one")
    bean.list.add("two")
    bean.list.add("three")
    doSerializerTest("<b>\n" + "  <list>\n" + "    <item value=\"one\" />\n" + "    <item value=\"two\" />\n" + "    <item value=\"three\" />\n" + "  </list>\n" + "</b>", bean, SkipDefaultsSerializationFilter())
  }

  @Test fun CollectionBean() {
    val bean = Bean4()
    bean.list.add("one")
    bean.list.add("two")
    bean.list.add("three")
    doSerializerTest("<b>\n" + "  <list>\n" + "    <item value=\"one\" />\n" + "    <item value=\"two\" />\n" + "    <item value=\"three\" />\n" + "  </list>\n" + "</b>", bean, SkipDefaultsSerializationFilter())
  }

  @Test fun CollectionBeanReadJDOMExternalizableStringList() {
    @Suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
    val list = JDOMExternalizableStringList()
    list.add("one")
    list.add("two")
    list.add("three")

    val value = Element("value")
    list.writeExternal(value)
    val o = Element("state").addContent(Element("option").setAttribute("name", "myList").addContent(value)).deserialize<Bean4>()
    assertSerializer(o, "<b>\n" + "  <list>\n" + "    <item value=\"one\" />\n" + "    <item value=\"two\" />\n" + "    <item value=\"three\" />\n" + "  </list>\n" + "</b>", SkipDefaultsSerializationFilter())
  }

  @Test fun polymorphicArray() {
    @Tag("bean")
    class BeanWithPolymorphicArray {
      @AbstractCollection(elementTypes = arrayOf(BeanWithPublicFields::class, BeanWithPublicFieldsDescendant::class))
      var v = arrayOf<BeanWithPublicFields>()
    }

    val bean = BeanWithPolymorphicArray()

    doSerializerTest("<bean>\n  <option name=\"v\">\n    <array />\n  </option>\n</bean>", bean)

    bean.v = arrayOf(BeanWithPublicFields(), BeanWithPublicFieldsDescendant(), BeanWithPublicFields())

    doSerializerTest("""<bean>
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
</bean>""", bean)
  }

  @Test fun arrayAnnotationWithoutTagNAmeGivesError() {
    val bean = BeanWithArrayWithoutTagName()
    assertThatThrownBy({
                         doSerializerTest(
                           "<BeanWithArrayWithoutTagName><option name=\"V\"><option value=\"a\"/></option></BeanWithArrayWithoutTagName>",
                           bean)
                       }).isInstanceOf(XmlSerializationException::class.java)
  }

  @Test fun arrayAnnotationWithElementTag() {
    @Tag("bean") class Bean {
      @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v")
      var v = arrayOf("a", "b")
    }

    val bean = Bean()

    doSerializerTest("""
    <bean>
      <option name="v">
        <array>
          <vvalue v="a" />
          <vvalue v="b" />
        </array>
      </option>
    </bean>""", bean)

    bean.v = arrayOf("1", "2", "3")

    doSerializerTest(
      "<bean>\n" + "  <option name=\"v\">\n" + "    <array>\n" + "      <vvalue v=\"1\" />\n" + "      <vvalue v=\"2\" />\n" + "      <vvalue v=\"3\" />\n" + "    </array>\n" + "  </option>\n" + "</bean>",
      bean)
  }

  @Test fun arrayWithoutTag() {
    @Tag("bean")
    class Bean {
      @AbstractCollection(elementTag = "vvalue", elementValueAttribute = "v", surroundWithTag = false)
      var v = arrayOf("a", "b")
      var INT_V = 1
    }

    val bean = Bean()

    doSerializerTest("""
    <bean>
      <option name="INT_V" value="1" />
      <option name="v">
        <vvalue v="a" />
        <vvalue v="b" />
      </option>
    </bean>""", bean)

    bean.v = arrayOf("1", "2", "3")

    doSerializerTest("""
    <bean>
      <option name="INT_V" value="1" />
      <option name="v">
        <vvalue v="1" />
        <vvalue v="2" />
        <vvalue v="3" />
      </option>
    </bean>""", bean)
  }

  private data class BeanWithArray(var ARRAY_V: Array<String> = arrayOf("a", "b"))

  @Test fun array() {
    val bean = BeanWithArray()
    doSerializerTest(
      "<BeanWithArray>\n  <option name=\"ARRAY_V\">\n    <array>\n      <option value=\"a\" />\n      <option value=\"b\" />\n    </array>\n  </option>\n</BeanWithArray>",
      bean)

    bean.ARRAY_V = arrayOf("1", "2", "3", "")
    doSerializerTest(
      "<BeanWithArray>\n  <option name=\"ARRAY_V\">\n    <array>\n      <option value=\"1\" />\n      <option value=\"2\" />\n      <option value=\"3\" />\n      <option value=\"\" />\n    </array>\n  </option>\n</BeanWithArray>",
      bean)
  }

  private class BeanWithList {
    var VALUES: List<String> = ArrayList(Arrays.asList("a", "b", "c"))
  }

  @Test fun ListSerialization() {
    val bean = BeanWithList()

    doSerializerTest(
      "<BeanWithList>\n  <option name=\"VALUES\">\n    <list>\n      <option value=\"a\" />\n      <option value=\"b\" />\n      <option value=\"c\" />\n    </list>\n  </option>\n</BeanWithList>",
      bean)

    bean.VALUES = ArrayList(Arrays.asList("1", "2", "3"))

    doSerializerTest(
      "<BeanWithList>\n  <option name=\"VALUES\">\n    <list>\n      <option value=\"1\" />\n      <option value=\"2\" />\n      <option value=\"3\" />\n    </list>\n  </option>\n</BeanWithList>",
      bean)
  }

  internal class BeanWithSet {
    var VALUES: Set<String> = LinkedHashSet(Arrays.asList("a", "b", "w"))
  }

  @Test fun SetSerialization() {
    val bean = BeanWithSet()
    doSerializerTest(
      "<BeanWithSet>\n  <option name=\"VALUES\">\n    <set>\n      <option value=\"a\" />\n      <option value=\"b\" />\n      <option value=\"w\" />\n    </set>\n  </option>\n</BeanWithSet>",
      bean)
    bean.VALUES = LinkedHashSet(Arrays.asList("1", "2", "3"))

    doSerializerTest(
      "<BeanWithSet>\n  <option name=\"VALUES\">\n    <set>\n      <option value=\"1\" />\n      <option value=\"2\" />\n      <option value=\"3\" />\n    </set>\n  </option>\n</BeanWithSet>",
      bean)
  }
}

@Tag("b")
private class Bean3 {
  @Suppress("DEPRECATED_SYMBOL_WITH_MESSAGE")
  var list = JDOMExternalizableStringList()
}

@Tag("b")
private class Bean4 {
  @CollectionBean
  val list = SmartList<String>()
}

private class BeanWithArrayWithoutTagName {
  @AbstractCollection(surroundWithTag = false)
  var V = arrayOf("a")
}