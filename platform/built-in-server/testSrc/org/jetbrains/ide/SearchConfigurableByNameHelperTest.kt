// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableGroup
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SearchConfigurableByNameHelperTest {
  @Test
  fun `one name`() {
    val result = createHelper("Bar", listOf(
      TestConfigurable("bar or not bar"),
      TestConfigurable("bar?"),
      TestConfigurable("!bar"),
      TestConfigurable("bar!!"),
      TestConfigurable("bar"))
    )
      .searchByName()

    assertThat(result!!.displayName).isEqualTo("bar")
  }

  @Test
  fun `one name in nested`() {
    val result = createHelper("Bar", listOf(
      TestConfigurable("child 1"),
      TestCompositeConfigurable("composite child", listOf(TestConfigurable("bar"), TestConfigurable("foo"))),
      TestConfigurable("child 3"))
    )
      .searchByName()

    assertThat(result!!.displayName).isEqualTo("bar")
  }

  @Test
  fun `name path`() {
    val result = createHelper("foo | bar | wanted", listOf(
      TestConfigurable("foo"),
      TestCompositeConfigurable("composite 1", listOf(TestCompositeConfigurable("bar or not bar", listOf(TestConfigurable("bar and co"))))),
      TestCompositeConfigurable("foo", listOf(TestCompositeConfigurable("bar", listOf(TestConfigurable("wanted"))))),
      TestCompositeConfigurable("composite 3", listOf()),
      TestConfigurable("bar"))
    )
      .searchByName() as TestConfigurable

    assertThat(result.displayName).isEqualTo("wanted")
    assertThat(result.parent!!.displayName).isEqualTo("bar")
  }

  @Test
  fun `name path - not found`() {
    val result = createHelper("foo | bar ", listOf(
      TestConfigurable("foo"),
      TestCompositeConfigurable("bar", listOf(TestConfigurable("foo"))),
      TestConfigurable("bar"))
    )
      .searchByName()

    assertThat(result).isNull()
  }

  // check that we do not go deeper until current level is not processed
  @Test
  fun `one name in direct after in nested`() {
    val result = createHelper("Bar", listOf(
      TestCompositeConfigurable("composite child", listOf(TestConfigurable("bar"), TestConfigurable("foo"))),
      TestConfigurable("bar"))
    )
      .searchByName() as TestConfigurable

    assertThat(result.displayName).isEqualTo("bar")
    assertThat(result.parent).isNull()
  }
}

private open class TestConfigurable(private val name: String) : Configurable {
  var parent: TestCompositeConfigurable? = null

  override fun getDisplayName() = name

  override fun isModified() = throw IllegalStateException()

  override fun apply() = throw IllegalStateException()

  override fun createComponent() = throw IllegalStateException()

  override fun toString() = name
}

private class TestCompositeConfigurable(name: String, private val configurables: List<TestConfigurable>) : TestConfigurable(name), Configurable.Composite {
  init {
    configurables.forEach {
      assert(it.parent == null)
      it.parent = this
    }
  }

  override fun getConfigurables() = configurables.toTypedArray()
}

private fun createHelper(query: String, configurables: List<Configurable>): SearchConfigurableByNameHelper {
  return SearchConfigurableByNameHelper(query, object : ConfigurableGroup {
    override fun getDisplayName() = "_root"

    override fun getConfigurables() = configurables.toTypedArray()
  })
}