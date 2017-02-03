package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.loadElement
import com.intellij.util.xmlb.XmlSerializer
import com.intellij.util.xmlb.annotations.Attribute
import org.junit.Test

private class AState : BaseState() {
  @get:Attribute("customName")
  var languageLevel by storedProperty<String?>()

  var property2 by storedProperty(0)

  var nestedComplex by storedProperty<NestedState?>()
}

private class NestedState : BaseState() {
  var childProperty by storedProperty<String?>()
}

class StoredPropertyStateTest {
  @Test
  fun test() {
    val state = AState()

    assertThat(state).isEqualTo(AState())

    assertThat(state.modificationCount).isEqualTo(0)
    assertThat(state.languageLevel).isNull()
    state.languageLevel = "foo"
    assertThat(state.modificationCount).isEqualTo(1)
    assertThat(state.languageLevel).isEqualTo("foo")
    assertThat(state.modificationCount).isEqualTo(1)

    assertThat(state).isNotEqualTo(AState())

    assertThat(XmlSerializer.serialize(state)).isEqualTo("""<AState customName="foo" />""")
    assertThat(XmlSerializer.deserialize(loadElement("""<AState customName="foo" />"""), AState::class.java)!!.languageLevel).isEqualTo("foo")
  }

  @Test
  fun childModificationCount() {
    val state = AState()
    assertThat(state.modificationCount).isEqualTo(0)
    val nestedState = NestedState()
    state.nestedComplex = nestedState
    assertThat(state.modificationCount).isEqualTo(1)

    nestedState.childProperty = "test"
    assertThat(state.modificationCount).isEqualTo(2)

    state.languageLevel = "11"
    assertThat(state.modificationCount).isEqualTo(3)

    state.languageLevel = null
    assertThat(state.modificationCount).isEqualTo(4)
  }
}