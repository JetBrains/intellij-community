// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.ModuleSet
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.DuplicateModuleSetPluginWrapperError
import org.jetbrains.intellij.build.productLayout.model.error.ModuleSetPluginizationError
import org.jetbrains.intellij.build.productLayout.model.error.UltimateModuleSetMainModuleError
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.jetbrains.intellij.build.productLayout.plugin
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class ModuleSetPluginizationValidatorTest {
  @Test
  fun `passes for pluginized module set without embedded modules`(): Unit = runBlocking(Dispatchers.Default) {
    val nested = moduleSet("nested") {
      module("nested.module")
    }
    val pluginized = plugin("pluginized") {
      module("module.a")
      moduleSet(nested)
    }

    val model = modelWithModuleSets(pluginized)
    val errors = runValidationRule(ModuleSetPluginizationValidator, model)

    assertThat(errors).isEmpty()
  }

  @Test
  fun `reports embedded modules in transitive closure`(): Unit = runBlocking(Dispatchers.Default) {
    val nestedWithEmbedded = moduleSet("nested.with.embedded") {
      embeddedModule("module.embedded.nested")
    }
    val pluginized = plugin("pluginized.with.embedded") {
      embeddedModule("module.embedded.direct")
      moduleSet(nestedWithEmbedded)
    }

    val model = modelWithModuleSets(pluginized)
    val errors = runValidationRule(ModuleSetPluginizationValidator, model)

    val pluginizationErrors = errors.filterIsInstance<ModuleSetPluginizationError>()
    assertThat(pluginizationErrors).hasSize(1)
    val error = pluginizationErrors.single()
    assertThat(error.context).isEqualTo("pluginized.with.embedded")
    assertThat(error.embeddedModules)
      .containsExactlyInAnyOrder(
        ContentModuleName("module.embedded.direct"),
        ContentModuleName("module.embedded.nested"),
      )
    assertThat(error.nestedPluginizedSets).isEmpty()
  }

  @Test
  fun `reports nested pluginized module sets`(): Unit = runBlocking(Dispatchers.Default) {
    val nestedPluginized = plugin("nested.pluginized") {
      module("module.nested")
    }
    val parentPluginized = plugin("parent.pluginized") {
      module("module.parent")
      moduleSet(nestedPluginized)
    }

    val model = modelWithModuleSets(parentPluginized)
    val errors = runValidationRule(ModuleSetPluginizationValidator, model)

    val pluginizationErrors = errors.filterIsInstance<ModuleSetPluginizationError>()
    assertThat(pluginizationErrors).hasSize(1)
    val error = pluginizationErrors.single()
    assertThat(error.context).isEqualTo("parent.pluginized")
    assertThat(error.embeddedModules).isEmpty()
    assertThat(error.nestedPluginizedSets).containsExactly("nested.pluginized")
  }

  @Test
  fun `reports duplicate wrapper module names across community and ultimate registries`(): Unit = runBlocking(Dispatchers.Default) {
    val duplicateCommunity = plugin("duplicate.wrapper") {
      module("community.module")
    }
    val duplicateUltimate = plugin("duplicate.wrapper") {
      module("ultimate.module")
    }

    val model = modelWithModuleSets(
      communityModuleSets = listOf(duplicateCommunity),
      ultimateModuleSets = listOf(duplicateUltimate),
    )
    val errors = runValidationRule(ModuleSetPluginizationValidator, model)

    assertThat(errors.filterIsInstance<DuplicateModuleSetPluginWrapperError>())
      .singleElement()
      .extracting { it.context }
      .isEqualTo("intellij.moduleSet.plugin.duplicate.wrapper")
  }

  @Test
  fun `reports ultimate wrappers that opt into community main module`(): Unit = runBlocking(Dispatchers.Default) {
    val ultimatePluginized = plugin("ultimate.main.module") {
      module("ultimate.module")
    }

    val model = modelWithModuleSets(ultimateModuleSets = listOf(ultimatePluginized))
    val errors = runValidationRule(ModuleSetPluginizationValidator, model)

    assertThat(errors.filterIsInstance<UltimateModuleSetMainModuleError>())
      .singleElement()
      .extracting { it.context }
      .isEqualTo("ultimate.main.module")
  }

  private fun modelWithModuleSets(
    vararg moduleSets: ModuleSet,
    communityModuleSets: List<ModuleSet> = moduleSets.toList(),
    ultimateModuleSets: List<ModuleSet> = emptyList(),
  ) =
    testGenerationModel(pluginGraph {})
      .let { model ->
        model.copy(
          discovery = model.discovery.copy(
            moduleSetsByLabel = buildMap {
              if (communityModuleSets.isNotEmpty()) put("community", communityModuleSets)
              if (ultimateModuleSets.isNotEmpty()) put("ultimate", ultimateModuleSets)
            }
          )
        )
      }
}
