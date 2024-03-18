// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.xml

import com.intellij.openapi.util.Ref
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import org.junit.jupiter.api.Test

internal class XmlSerializerConversionTest {
  @Test
  fun converter() {
    val bean = BeanWithConverter()
    testSerializer(
      expectedXml = """
        <bean>
          <option name="bar" />
        </bean>
      """,
      expectedJson = """
        {
          "foo": null,
          "bar": null
        }
      """,
      expectedJsonByXml = """
        {
          "bar": null
        }
      """,
      bean = bean,
    )

    bean.foo = Ref("testValue")
    testSerializer(
      expectedXml = """
        <bean foo="testValue">
          <option name="bar" />
        </bean>
      """,
      expectedJson = """
        {
          "foo": "testValue",
          "bar": null
        }
      """,
      bean = bean,
    )

    bean.foo = Ref()
    bean.bar = Ref("testValue2")
    testSerializer(
      expectedXml = """
        <bean foo="">
          <option name="bar" value="testValue2" />
        </bean>
      """,
      expectedJson = """
        {
          "foo": "",
          "bar": "testValue2"
        }
      """,
      bean = bean,
    )
  }

  @Test
  fun converterUsingSkipDefaultsFilter() {
    val bean = BeanWithConverter()
    testSerializer(
      expectedXml = "<bean />",
      expectedJson = """
        {}
      """,
      bean = bean,
      filter = SkipDefaultsSerializationFilter(),
    )

    bean.foo = Ref.create("testValue")
    testSerializer(
      expectedXml = """<bean foo="testValue" />""",
      expectedJson = """
        {
          "foo": "testValue"
        }
      """,
      bean = bean,
      filter = SkipDefaultsSerializationFilter(),
    )

    bean.foo = Ref()
    bean.bar = Ref("testValue2")
    testSerializer(
      expectedXml = """
        <bean foo="">
          <option name="bar" value="testValue2" />
        </bean>
      """,
      expectedJson = """
        {
          "foo": "",
          "bar": "testValue2"
        }
      """,
      bean = bean,
    )
  }
}

@Tag("bean")
private class BeanWithConverter {
  private class MyConverter : Converter<Ref<String>>() {
    override fun fromString(value: String): Ref<String> {
      return Ref(value)
    }

    override fun toString(value: Ref<String>): String {
      return (value.get() ?: "")
    }
  }

  @Attribute(converter = MyConverter::class)
  var foo: Ref<String>? = null

  @OptionTag(converter = MyConverter::class)
  var bar: Ref<String>? = null
}