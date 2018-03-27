package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.loadElement
import com.intellij.util.xmlb.annotations.Attribute
import org.junit.Test

internal class AState(languageLevel: String? = null, nestedComplex: NestedState? = null) : BaseState() {
  @get:Attribute("customName")
  var languageLevel by property(languageLevel)

  var bar by string()

  var property2 by property(0)

  var floatProperty by property(0.3f)

  var nestedComplex by property(nestedComplex)
}

internal class NestedState : BaseState() {
  var childProperty by property<String?>()
}

class StoredPropertyStateTest {
  private class Foo : BaseState() {
    var bar by property<AState>()
  }

  private class Foo2 : BaseState() {
    var bar: AState? by property(AState())
  }

  @Test
  fun `default null equals to bean with default property values`() {
    val f1 = Foo()
    val f2 = Foo()
    f2.bar = AState()

    assertThat(f1.serialize()).isNull()
    assertThat(f2.serialize()).isNull()
  }

  @Test
  fun `bean with default property values equals to default null`() {
    val f1 = Foo2()
    val f2 = Foo2()
    f2.bar = null

    assertThat(f1.serialize()).isNull()
    assertThat(f2.serialize()).isNull()
  }

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