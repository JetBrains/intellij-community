// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.configurationStore.xml

import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import org.junit.jupiter.api.Test

internal class XmlSerializerOldMapAnnotationTest {
  @Test
  fun beanValueUsingSkipDefaultsFilter() {
    @Tag("bean")
    class Bean {
      @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
      var values: Map<String, BeanWithProperty> = HashMap()
    }

    val bean = Bean()
    testSerializer(
      expectedXml = "<bean />",
      expectedJson = """
        {}
      """,
      bean = bean,
      filter = SkipDefaultsSerializationFilter(),
    )
  }

  @Test fun mapAtTopLevel() {
    @Tag("bean")
    class BeanWithMapAtTopLevel {
      @Property(surroundWithTag = false)
      @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
      var map = LinkedHashMap<String, String>()

      var option: String? = null
    }

    val bean = BeanWithMapAtTopLevel()
    bean.map.put("a", "b")
    bean.option = "xxx"
    testSerializer(
      expectedXml = """
        <bean>
          <option name="option" value="xxx" />
          <entry key="a" value="b" />
        </bean>
      """,
      expectedJson = """
        {
          "option": "xxx",
          "map": {
            "a": "b"
          }
        }
      """,
      bean = bean,
    )
  }

  @Test fun notSurroundingKeyAndValue() {
    @Tag("bean")
    class Bean {
      @Tag("map")
      @MapAnnotation(surroundWithTag = false, entryTagName = "pair", surroundKeyWithTag = false, surroundValueWithTag = false)
      var MAP = LinkedHashMap<BeanWithPublicFields, BeanWithTextAnnotation>()
    }

    val bean = Bean()

    bean.MAP.put(BeanWithPublicFields(1, "a"), BeanWithTextAnnotation(2, "b"))
    bean.MAP.put(BeanWithPublicFields(3, "c"), BeanWithTextAnnotation(4, "d"))
    bean.MAP.put(BeanWithPublicFields(5, "e"), BeanWithTextAnnotation(6, "f"))

    testSerializer(
      expectedXml = """
        <bean>
          <map>
            <pair>
              <BeanWithPublicFields>
                <option name="INT_V" value="1" />
                <option name="STRING_V" value="a" />
              </BeanWithPublicFields>
              <BeanWithTextAnnotation>
                <option name="INT_V" value="2" />
                b
              </BeanWithTextAnnotation>
            </pair>
            <pair>
              <BeanWithPublicFields>
                <option name="INT_V" value="3" />
                <option name="STRING_V" value="c" />
              </BeanWithPublicFields>
              <BeanWithTextAnnotation>
                <option name="INT_V" value="4" />
                d
              </BeanWithTextAnnotation>
            </pair>
            <pair>
              <BeanWithPublicFields>
                <option name="INT_V" value="5" />
                <option name="STRING_V" value="e" />
              </BeanWithPublicFields>
              <BeanWithTextAnnotation>
                <option name="INT_V" value="6" />
                f
              </BeanWithTextAnnotation>
            </pair>
          </map>
        </bean>
      """,
      expectedJson = """
        {
          "map": [
            {
              "key": {
                "int_v": 1,
                "string_v": "a"
              },
              "value": {
                "int_v": 2,
                "string_v": "b"
              }
            },
            {
              "key": {
                "int_v": 3,
                "string_v": "c"
              },
              "value": {
                "int_v": 4,
                "string_v": "d"
              }
            },
            {
              "key": {
                "int_v": 5,
                "string_v": "e"
              },
              "value": {
                "int_v": 6,
                "string_v": "f"
              }
            }
          ]
      }
      """,
      bean = bean,
    )
  }

  @Test fun beanWithMapWithSetValue() {
    @Tag("bean")
    class BeanWithMapWithSetValue {
      @MapAnnotation(entryTagName = "entry-tag", keyAttributeName = "key-attr", surroundWithTag = false)
      var values = LinkedHashMap<String, Set<String>>()
    }

    val bean = BeanWithMapWithSetValue()

    bean.values.put("a", linkedSetOf("first1", "second1"))
    bean.values.put("b", linkedSetOf("first2", "second2"))

    testSerializer(
      expectedXml = """
        <bean>
          <option name="values">
            <entry-tag key-attr="a">
              <value>
                <set>
                  <option value="first1" />
                  <option value="second1" />
                </set>
              </value>
            </entry-tag>
            <entry-tag key-attr="b">
              <value>
                <set>
                  <option value="first2" />
                  <option value="second2" />
                </set>
              </value>
            </entry-tag>
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "values": {
            "a": [
              "first1",
              "second1"
            ],
            "b": [
              "first2",
              "second2"
            ]
          }
        }
      """,
      bean = bean,
    )
  }

  private class BeanWithMapWithAnnotations {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, entryTagName = "option", keyAttributeName = "name", valueAttributeName = "value")
    var VALUES: MutableMap<String, String> = LinkedHashMap()

    init {
      VALUES.put("a", "1")
      VALUES.put("b", "2")
      VALUES.put("c", "3")
    }
  }

  @Test
  fun serializationWithAnnotations() {
    val bean = BeanWithMapWithAnnotations()
    testSerializer(
      expectedXml = """
        <BeanWithMapWithAnnotations>
          <option name="a" value="1" />
          <option name="b" value="2" />
          <option name="c" value="3" />
        </BeanWithMapWithAnnotations>
      """,
      expectedJson = """
        {
          "values": {
            "a": "1",
            "b": "2",
            "c": "3"
          }
        }
      """,
      bean = bean,
    )
    bean.VALUES.clear()
    bean.VALUES.put("1", "a")
    bean.VALUES.put("2", "b")
    bean.VALUES.put("3", "c")

    testSerializer(
      expectedXml = """
        <BeanWithMapWithAnnotations>
          <option name="1" value="a" />
          <option name="2" value="b" />
          <option name="3" value="c" />
        </BeanWithMapWithAnnotations>
      """,
      expectedJson = """
        {
          "values": {
            "1": "a",
            "2": "b",
            "3": "c"
          }
        }
      """,
      bean = bean,
    )
  }

  @Test
  fun `propertyWithoutSurroundingElement`() {
    @Tag("branch-storage")
    class BranchStorage {
      @Property(surroundWithTag = false)
      @MapAnnotation(keyAttributeName = "type")
      @JvmField
      var branches = HashMap<String, String>()
    }

    val bean = BranchStorage()
    bean.branches.put("branchName", "foo")

    testSerializer(
      expectedXml = """
        <branch-storage>
          <map>
            <entry type="branchName" value="foo" />
          </map>
        </branch-storage>
      """,
      expectedJson = """
        {
          "branches": {
            "branchName": "foo"
          }
        }
      """,
      bean = bean,
      filter = SkipDefaultsSerializationFilter(),
    )
  }
}