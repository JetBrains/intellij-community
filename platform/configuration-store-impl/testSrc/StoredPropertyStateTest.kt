package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.loadElement
import com.intellij.util.xmlb.annotations.Attribute
import org.junit.Test

internal class AState(languageLevel: String? = null, nestedComplex: NestedState? = null) : BaseState() {
  @get:Attribute("customName")
  var languageLevel by storedProperty<String?>(languageLevel)

  var bar by string()

  var property2 by storedProperty(0)

  var floatProperty by storedProperty(0.3)

  var nestedComplex by storedProperty<NestedState?>(nestedComplex)
}

internal class NestedState : BaseState() {
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

    val newEqualState = AState()
    newEqualState.languageLevel = String("foo".toCharArray())
    assertThat(state).isEqualTo(newEqualState)

    assertThat(state.serialize()).isEqualTo("""<AState customName="foo" />""")
    assertThat(loadElement("""<AState customName="foo" />""").deserialize(AState::class.java).languageLevel).isEqualTo("foo")
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

    state.copyFrom(AState("foo", nestedState))
    @Suppress("USELESS_CAST")
    assertThat(state.languageLevel as String?).isEqualTo("foo")
    assertThat(state.modificationCount).isEqualTo(5)
  }
}