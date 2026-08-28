// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.PluginModuleId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.DuplicatePluginContentModulesError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class PluginContentDuplicatesValidatorTest {
  @Test
  fun `reports duplicate between production and test plugin`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("prod.plugin")
        bundlesTestPlugin("test.plugin")
      }
      plugin("prod.plugin") { content("shared.module") }
      testPlugin("test.plugin") { content("shared.module") }
    }

    val model = testGenerationModel(graph)
    val errors = runValidationRule(PluginContentDuplicatesValidator, model)

    assertThat(errors).hasSize(1)
    val error = errors[0] as DuplicatePluginContentModulesError
    val owners = error.duplicates[PluginModuleId("shared.module", namespace = "jetbrains")]
    assertThat(owners).isNotNull
    assertThat(owners!!.map { it.pluginName.value })
      .containsExactlyInAnyOrder("prod.plugin", "test.plugin")
    assertThat(owners.any { it.isTestPlugin }).isTrue()
  }

  @Test
  fun `do not report duplicate if different namespaces are used`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("prod.plugin")
        bundlesTestPlugin("test.plugin")
      }
      plugin("prod.plugin") { content("shared.module", namespace = null) }
      testPlugin("test.plugin") { content("shared.module", namespace = null) }
    }

    val model = testGenerationModel(graph)
    val errors = runValidationRule(PluginContentDuplicatesValidator, model)

    assertThat(errors).isEmpty()
  }

  @Test
  fun `ignores duplicate between production plugins`(): Unit = runBlocking(Dispatchers.Default) {
    // A `<content>` tag with no namespace asks for a private copy per plugin, which IJPL-A-1893 allows.
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("plugin.a")
        bundlesPlugin("plugin.b")
      }
      plugin("plugin.a") { content("shared.module", namespace = null) }
      plugin("plugin.b") { content("shared.module", namespace = null) }
    }

    val model = testGenerationModel(graph)
    val errors = runValidationRule(PluginContentDuplicatesValidator, model)

    assertThat(errors).isEmpty()
  }

  @Test
  fun `ignores duplicates between test plugins`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesTestPlugin("test.a")
        bundlesTestPlugin("test.b")
      }
      testPlugin("test.a") { content("shared.module") }
      testPlugin("test.b") { content("shared.module") }
    }

    val model = testGenerationModel(graph)
    val errors = runValidationRule(PluginContentDuplicatesValidator, model)

    assertThat(errors).isEmpty()
  }

  @Test
  fun `reports duplicate between production plugins in the same namespace`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("plugin.a")
        bundlesPlugin("plugin.b")
      }
      plugin("plugin.a") { content("shared.module", namespace = PluginModuleId.DEFAULT_NAMESPACE) }
      plugin("plugin.b") { content("shared.module", namespace = PluginModuleId.DEFAULT_NAMESPACE) }
    }

    val model = testGenerationModel(graph)
    val errors = runValidationRule(PluginContentDuplicatesValidator, model)

    assertThat(errors).hasSize(1)
    val error = errors[0] as DuplicatePluginContentModulesError
    val owners = error.duplicates[PluginModuleId("shared.module", namespace = PluginModuleId.DEFAULT_NAMESPACE)]
    assertThat(owners).isNotNull
    assertThat(owners!!.map { it.pluginName.value })
      .containsExactlyInAnyOrder("plugin.a", "plugin.b")
    assertThat(owners.any { it.isTestPlugin }).isFalse()
  }

  @Test
  fun `reports duplicate between production plugins in one custom namespace`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("plugin.a")
        bundlesPlugin("plugin.b")
      }
      plugin("plugin.a") { content("shared.module", namespace = "custom") }
      plugin("plugin.b") { content("shared.module", namespace = "custom") }
    }

    val model = testGenerationModel(graph)
    val errors = runValidationRule(PluginContentDuplicatesValidator, model)

    assertThat(errors).hasSize(1)
    val error = errors[0] as DuplicatePluginContentModulesError
    assertThat(error.duplicates.keys)
      .containsExactlyInAnyOrder(PluginModuleId("shared.module", namespace = "custom"))
  }

  @Test
  fun `ignores duplicate between production plugins without a namespace`(): Unit = runBlocking(Dispatchers.Default) {
    // The implicit namespace comes from the plugin ID, so the two runtime IDs differ.
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("plugin.a")
        bundlesPlugin("plugin.b")
      }
      plugin("plugin.a") {
        pluginId("com.example.a")
        content("shared.module", namespace = null)
      }
      plugin("plugin.b") {
        pluginId("com.example.b")
        content("shared.module", namespace = null)
      }
    }

    val model = testGenerationModel(graph)
    val errors = runValidationRule(PluginContentDuplicatesValidator, model)

    assertThat(errors).isEmpty()
  }

  @Test
  fun `ignores duplicate between production plugins with different namespaces`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("plugin.a")
        bundlesPlugin("plugin.b")
      }
      plugin("plugin.a") { content("shared.module", namespace = PluginModuleId.DEFAULT_NAMESPACE) }
      plugin("plugin.b") { content("shared.module", namespace = "custom") }
    }

    val model = testGenerationModel(graph)
    val errors = runValidationRule(PluginContentDuplicatesValidator, model)

    assertThat(errors).isEmpty()
  }
}
