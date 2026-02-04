// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
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
    val owners = error.duplicates[ContentModuleName("shared.module")]
    assertThat(owners).isNotNull
    assertThat(owners!!.map { it.pluginName.value })
      .containsExactlyInAnyOrder("prod.plugin", "test.plugin")
    assertThat(owners.any { it.isTestPlugin }).isTrue()
  }

  @Test
  fun `ignores duplicate between production plugins`(): Unit = runBlocking(Dispatchers.Default) {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("plugin.a")
        bundlesPlugin("plugin.b")
      }
      plugin("plugin.a") { content("shared.module") }
      plugin("plugin.b") { content("shared.module") }
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
}
