// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import org.junit.jupiter.api.Test

class ElementStateTest {
  @Test
  fun `an attribute change preserves explicit default text beside a child`() {
    val stored = JDOMUtil.load("""<component other="A">default text<child value="X" /></component>""")
    val before = JDOMUtil.load("""<component other="A"><child value="X" /></component>""")
    val after = JDOMUtil.load("""<component other="B"><child value="X" /></component>""")
    val merged = mergeElementState(stored, before, after)!!
    assertThat(merged.text).isEqualTo("default text")
    assertThat(merged.getAttributeValue("other")).isEqualTo("B")
    assertThat(merged.getChild("child").getAttributeValue("value")).isEqualTo("X")
  }

  @Test
  fun `a text change beside a child replaces the stored text`() {
    val stored = JDOMUtil.load("""<component>old text<child value="X" /></component>""")
    val before = stored.clone()
    val after = JDOMUtil.load("""<component>new text<child value="X" /></component>""")
    assertThat(mergeElementState(stored, before, after)).isEqualTo(after)
  }

  @Test
  fun `a text reset beside a child removes the stored text`() {
    val stored = JDOMUtil.load("""<component>old text<child value="X" /></component>""")
    val before = stored.clone()
    val after = JDOMUtil.load("""<component><child value="X" /></component>""")
    assertThat(mergeElementState(stored, before, after)).isEqualTo(after)
  }

  @Test
  fun `preserved default text does not undo a child order change`() {
    val stored = JDOMUtil.load("""<component>default text<first /><second /></component>""")
    val before = JDOMUtil.load("""<component><first /><second /></component>""")
    val after = JDOMUtil.load("""<component><second /><first /></component>""")
    val merged = mergeElementState(stored, before, after)!!
    assertThat(merged.text).isEqualTo("default text")
    assertThat(merged.children.map { it.name }).containsExactly("second", "first")
  }

  @Test
  fun `keyed entries are added removed and changed independently`() {
    val stored = JDOMUtil.load("""<component><entry key="a" value="1" /><entry key="b" value="2" /><entry key="c" value="3" /></component>""")
    val before = stored.clone()
    val after = JDOMUtil.load("""<component><entry key="a" value="1" /><entry key="c" value="30" /><entry key="d" value="4" /></component>""")
    val merged = mergeElementState(stored, before, after)!!
    assertThat(merged.children.associate { it.getAttributeValue("key") to it.getAttributeValue("value") })
      .isEqualTo(mapOf("a" to "1", "c" to "30", "d" to "4"))
  }

  @Test
  fun `a stored entry that is absent from before and after survives`() {
    val stored = JDOMUtil.load("""<component><entry key="a" value="1" /><entry key="hidden" value="x" /></component>""")
    val before = JDOMUtil.load("""<component><entry key="a" value="1" /></component>""")
    val after = JDOMUtil.load("""<component><entry key="a" value="2" /></component>""")
    val merged = mergeElementState(stored, before, after)!!
    assertThat(merged.children.associate { it.getAttributeValue("key") to it.getAttributeValue("value") })
      .isEqualTo(mapOf("a" to "2", "hidden" to "x"))
  }

  @Test
  fun `repeated children without a unique key are replaced as one list`() {
    val stored = JDOMUtil.load("""<component><item value="1" /><item value="1" /></component>""")
    val before = stored.clone()
    val after = JDOMUtil.load("""<component><item value="1" /><item value="2" /><item value="1" /></component>""")
    val merged = mergeElementState(stored, before, after)!!
    assertThat(merged.children.map { it.getAttributeValue("value") }).containsExactly("1", "2", "1")
  }

  @Test
  fun `unchanged repeated children keep the stored list`() {
    val stored = JDOMUtil.load("""<component><item value="1" /><item value="1" /><item value="stored" /></component>""")
    val before = JDOMUtil.load("""<component><item value="1" /><item value="1" /></component>""")
    val after = before.clone()
    val merged = mergeElementState(stored, before, after)!!
    assertThat(merged.children.map { it.getAttributeValue("value") }).containsExactly("1", "1", "stored")
  }

  @Test
  fun `a missing stored or before element uses the new state`() {
    val after = JDOMUtil.load("""<component><entry key="a" value="2" /></component>""")
    assertThat(mergeElementState(null, JDOMUtil.load("<component />"), after)).isEqualTo(after)
    assertThat(mergeElementState(JDOMUtil.load("""<component><entry key="x" value="1" /></component>"""), null, after)).isEqualTo(after)
    assertThat(mergeElementState(JDOMUtil.load("<component />"), JDOMUtil.load("<component />"), null)).isNull()
  }
}
