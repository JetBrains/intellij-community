// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "PropertyName")

package com.intellij.configurationStore.xml

import com.intellij.configurationStore.AState
import com.intellij.configurationStore.deserialize
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.util.JDOMUtil
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.TreeMap

class KotlinXmlSerializerTest {
  @Test
  fun internalVar() {
    @Tag("bean")
    class Foo {
      var PLACES_MAP = ""
    }

    val data = Foo()
    data.PLACES_MAP = "new"
    testSerializer(
      expectedXml = """
        <bean>
          <option name="PLACES_MAP" value="new" />
        </bean>
      """,
      expectedJson = """
        {
          "places_map": "new"
        }
      """,
      bean = data,
    )
  }

  @Tag("profile-state")
  private class VisibleTreeState : BaseState() {
    @Suppress("unused")
    var foo by string()
  }

  private class VisibleTreeStateComponent : BaseState() {
    // we do not support private accessors
    @get:Property(surroundWithTag = false)
    @get:MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
    var profileNameToState by map<String, VisibleTreeState>()

    fun getVisibleTreeState(profile: String) = profileNameToState.computeIfAbsent(profile) {
      incrementModificationCount()
      VisibleTreeState()
    }
  }


  @Test fun `private map`() {
    val data = VisibleTreeStateComponent()
    data.getVisibleTreeState("new")
    testSerializer(
      expectedXml = """
        <VisibleTreeStateComponent>
          <entry key="new">
            <profile-state />
          </entry>
        </VisibleTreeStateComponent>
      """,
      expectedJson = """
        {
          "profileNameToState": {
            "new": {}
          }
        }
      """,
      bean = data,
    )
  }

  @Test
  fun floatProperty() {
    val state = AState()
    state.floatProperty = 3.4f
    testSerializer(
      expectedXml = """
        <AState>
          <option name="floatProperty" value="3.4" />
        </AState>
      """,
      expectedJson = """
        {
          "floatProperty": 3.4
        }
      """,
      bean = state,
    )
  }

  @Test fun nullInMap() {
    @Tag("bean")
    class Foo {
      @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false)
      var PLACES_MAP: TreeMap<String, PlaceSettings> = TreeMap()
    }

    val data = Foo()
    data.PLACES_MAP.put("", PlaceSettings())
    testSerializer(
      expectedXml = """
        <bean>
          <option name="PLACES_MAP">
            <entry key="">
              <PlaceSettings>
                <option name="IGNORE_POLICY" value="DEFAULT" />
              </PlaceSettings>
            </entry>
          </option>
        </bean>
      """,
      expectedJson = """
        {
          "places_map": {
            "": {
              "ignore_policy": "DEFAULT"
            }
          }
        }
      """,
      bean = data,
    )

    assertThat(deserialize<Foo>(element = JDOMUtil.load("""<bean>
                <option name="PLACES_MAP">
                  <entry key="">
                    <PlaceSettings>
                      <option name="IGNORE_POLICY" />
                    </PlaceSettings>
                  </entry>
                </option>
              </bean>""")).PLACES_MAP.get("")!!.IGNORE_POLICY).isEqualTo(IgnorePolicy.DEFAULT)

    val value = deserialize<Foo>(JDOMUtil.load("""<bean>
          <option name="PLACES_MAP">
            <entry key="">
              <PlaceSettings>
                <option name="SOME_UNKNOWN_VALUE" />
              </PlaceSettings>
            </entry>
          </option>
        </bean>"""))
    assertThat(value).isNotNull()
    val placeSettings = value.PLACES_MAP.get("")
    assertThat(placeSettings).isNotNull()
    assertThat(placeSettings!!.IGNORE_POLICY).isEqualTo(IgnorePolicy.DEFAULT)
  }
}

@Suppress("unused")
private enum class IgnorePolicy(val text: String) {
  DEFAULT("Do not ignore"),
  TRIM_WHITESPACES("Trim whitespaces"),
  IGNORE_WHITESPACES("Ignore whitespaces"),
  IGNORE_WHITESPACES_CHUNKS("Ignore whitespaces and empty lines"),
  FORMATTING("Ignore formatting");
}

private data class PlaceSettings(var IGNORE_POLICY: IgnorePolicy = IgnorePolicy.DEFAULT)
