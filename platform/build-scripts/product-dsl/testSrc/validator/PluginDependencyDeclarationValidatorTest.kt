// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.deps.PluginDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.PluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.model.error.DuplicatePluginDependencyDeclarationError
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class PluginDependencyDeclarationValidatorTest {
  @Test
  fun `duplicate legacy and modern plugin deps report error`(): Unit = runBlocking(Dispatchers.Default) {
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
    val plan = PluginDependencyPlan(
      pluginContentModuleName = ContentModuleName("plugin.a"),
      pluginXmlPath = Path.of("plugin.a/META-INF/plugin.xml"),
      pluginXmlContent = "",
      moduleDependencies = emptyList(),
      pluginDependencies = emptyList(),
      legacyPluginDependencies = emptyList(),
      xiIncludeModuleDeps = emptySet(),
      xiIncludePluginDeps = emptySet(),
      existingXmlModuleDependencies = emptySet(),
      existingXmlPluginDependencies = emptySet(),
      preserveExistingModuleDependencies = emptySet(),
      preserveExistingPluginDependencies = emptySet(),
      suppressionUsages = emptyList(),
      duplicateDeclarationPluginIds = setOf(PluginId("com.b")),
    )
    val errors = runValidationRule(
      PluginDependencyDeclarationValidator,
      model,
      slotOverrides = mapOf(Slots.PLUGIN_DEPENDENCY_PLAN to PluginDependencyPlanOutput(plans = listOf(plan))),
    )

      val dupErrors = errors.filterIsInstance<DuplicatePluginDependencyDeclarationError>()
      assertThat(dupErrors).hasSize(1)
      assertThat(dupErrors.first().duplicatePluginIds).containsExactly(PluginId("com.b"))
  }
}
