// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.PluginDescriptorIdConflictError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class PluginDescriptorIdConflictValidatorTest {
  @Test
  fun `reports conflicts between production and test descriptor ids`() {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("prod.plugin")
        bundlesTestPlugin("test.plugin")
      }
      plugin("prod.plugin") {
        pluginId("com.example.prod")
        content("intellij.pycharm.pro.customizationJupyter")
      }
      testPlugin("test.plugin") {
        pluginId("intellij.python.junit5Tests.plugin")
        content("intellij.pycharm.pro.customizationJupyter")
      }
    }

    val model = testGenerationModel(graph)
    val errors = runBlocking { runValidationRule(PluginDescriptorIdConflictValidator, model) }

    val conflictErrors = errors.filterIsInstance<PluginDescriptorIdConflictError>()
    assertThat(conflictErrors).hasSize(1)

    val owners = conflictErrors.single().duplicates[PluginId("intellij.pycharm.pro.customizationJupyter")]
    assertThat(owners).isNotNull
    assertThat(owners!!.map { it.pluginName.value })
      .containsExactlyInAnyOrder("prod.plugin", "test.plugin")
    assertThat(owners.any { it.isTestPlugin }).isTrue()
  }

  @Test
  fun `ignores distinct production and test descriptor ids`() {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesPlugin("prod.plugin")
        bundlesTestPlugin("test.plugin")
      }
      plugin("prod.plugin") {
        pluginId("com.example.prod")
        content("intellij.prod.module")
      }
      testPlugin("test.plugin") {
        pluginId("com.example.test")
        content("intellij.test.module")
      }
    }

    val model = testGenerationModel(graph)
    val errors = runBlocking { runValidationRule(PluginDescriptorIdConflictValidator, model) }

    assertThat(errors).isEmpty()
  }
}
