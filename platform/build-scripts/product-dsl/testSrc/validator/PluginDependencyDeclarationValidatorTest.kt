// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.DuplicatePluginDependencyDeclarationError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class PluginDependencyDeclarationValidatorTest {
  @Test
  fun `duplicate legacy and modern plugin deps report error`() {
    val graph = pluginGraph {
      plugin("plugin.a") {
        pluginId("com.a")
        dependsOnPlugin("com.b")
        dependsOnLegacyPlugin("com.b")
      }
      plugin("plugin.b") {
        pluginId("com.b")
      }
    }

    val model = testGenerationModel(graph)
    val errors = runBlocking { runValidationRule(PluginDependencyDeclarationValidator, model) }

    val dupErrors = errors.filterIsInstance<DuplicatePluginDependencyDeclarationError>()
    assertThat(dupErrors).hasSize(1)
    assertThat(dupErrors.first().duplicatePluginIds).containsExactly(PluginId("com.b"))
  }
}
