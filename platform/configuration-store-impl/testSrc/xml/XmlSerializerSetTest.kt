// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.xml

import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import org.junit.jupiter.api.Test

class XmlSerializerSetTest {
  @Test
  fun `empty set`() {
    @Tag("bean")
    class Bean {
      @JvmField
      var values = emptySet<String>()
    }

    val data = Bean()
    data.values = setOf("foo")
    testSerializer(
      expectedXml = """
        <bean>
          <option name="values">
            <set>
              <option value="foo" />
            </set>
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
  fun notFinalField() {
    @Tag("bean")
    class BeanWithSet {
      @JvmField
      var values = linkedSetOf("a", "b", "w")
    }

    val bean = BeanWithSet()
    assertSet(bean) {
      bean.values = it
    }
  }

  @Test
  fun notFinalProperty() {
    @Tag("bean")
    class BeanWithSet {
      var values = linkedSetOf("a", "b", "w")
    }

    val bean = BeanWithSet()
    assertSet(bean) {
      bean.values = it
    }
  }

  @Test
  fun finalField() {
    @Tag("bean")
    class BeanWithSet {
      @JvmField
      val values = linkedSetOf("a", "b", "w")
    }

    val bean = BeanWithSet()
    assertSet(bean) {
      bean.values.clear()
      bean.values.addAll(it)
    }
  }

  @Test
  fun finalProperty() {
    @Tag("bean")
    class BeanWithSet {
      @OptionTag
      val values = linkedSetOf("a", "b", "w")
    }

    val bean = BeanWithSet()
    assertSet(bean) {
      bean.values.clear()
      bean.values.addAll(it)
    }
  }

  @Test
  fun finalPropertyWithoutWrapping() {
    @Tag("bean")
    class Bean {
      @XCollection
      val values = linkedSetOf("a", "b", "w")
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
        </bean>""",
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
    bean.values.addAll(linkedSetOf("1", "2", "3"))

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
      bean = bean)
  }

  private fun <T : Any> assertSet(bean: T, setter: (values: LinkedHashSet<String>) -> Unit) {
    testSerializer(
      expectedXml = """
        <bean>
          <option name="values">
            <set>
              <option value="a" />
              <option value="b" />
              <option value="w" />
            </set>
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
    setter(linkedSetOf("1", "2", "3"))

    testSerializer(
      expectedXml = """
        <bean>
          <option name="values">
            <set>
              <option value="1" />
              <option value="2" />
              <option value="3" />
            </set>
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
      bean = bean)
  }
}