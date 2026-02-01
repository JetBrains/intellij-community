// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.PluginDependencyError
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * Isolated unit tests for [PluginContentStructureValidator].
 */
@ExtendWith(TestFailureLogger::class)
class PluginContentStructureValidatorTest {
  @Test
  fun `test plugin structural violations include test content modules`() {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesTestPlugin("test.plugin")
        includesModuleSet("core")
      }
      testPlugin("test.plugin") {
        testContent("mod.required", ModuleLoadingRuleValue.REQUIRED)
        testContent("mod.optional", ModuleLoadingRuleValue.OPTIONAL)
      }
      moduleSet("core") {
        module("mod.optional")
      }
      linkContentModuleTestDeps("mod.required", "mod.optional")
    }

    val model = testGenerationModel(graph)
    val errors = runBlocking { runValidationRule(PluginContentStructureValidator, model) }

    assertThat(errors).hasSize(1)
    val error = errors[0] as PluginDependencyError
    assertThat(error.structuralViolations)
      .containsEntry(ContentModuleName("mod.required"), setOf(ContentModuleName("mod.optional")))
    assertThat(error.missingDependencies).isEmpty()
  }

  @Test
  fun `test plugin structural violations include production edges`() {
    val graph = pluginGraph {
      product("IDEA") {
        bundlesTestPlugin("test.plugin")
      }
      testPlugin("test.plugin") {
        testContent("mod.required", ModuleLoadingRuleValue.REQUIRED)
        testContent("mod.optional", ModuleLoadingRuleValue.OPTIONAL)
      }
      linkContentModuleDeps("mod.required", "mod.optional")
    }

    val model = testGenerationModel(graph)
    val errors = runBlocking { runValidationRule(PluginContentStructureValidator, model) }

    assertThat(errors).hasSize(1)
    val error = errors[0] as PluginDependencyError
    assertThat(error.structuralViolations)
      .containsEntry(ContentModuleName("mod.required"), setOf(ContentModuleName("mod.optional")))
  }
}
