// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.plugins.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.MissingContentModulePluginDependencyError
import org.jetbrains.intellij.build.productLayout.pipeline.ContentModuleOutput
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class ContentModulePluginDependencyValidatorTest {
  @Test
  fun `missing plugin dependency in XML reports error`() {
    val graph = pluginGraph {
      plugin("owner.plugin") {
        pluginId("owner.plugin")
        content("owner.content", ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
      }
    }

    val result = contentModuleResult(
      moduleName = "owner.content",
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = setOf(PluginId("dep.plugin")),
    )

    val model = testGenerationModel(graph)
    val errors = runBlocking {
      runValidationRule(
        ContentModulePluginDependencyValidator,
        model,
        slotOverrides = mapOf(Slots.CONTENT_MODULE to ContentModuleOutput(files = listOf(result))),
      )
    }

    val missingErrors = errors.filterIsInstance<MissingContentModulePluginDependencyError>()
    assertThat(missingErrors).hasSize(1)
    assertThat(missingErrors.first().missingPluginIds).containsExactly(PluginId("dep.plugin"))
  }

  @Test
  fun `containing plugin dependency is ignored`() {
    val contentModuleName = "owner.content.alt"
    val graph = pluginGraph {
      plugin("owner.plugin") {
        pluginId("owner.plugin")
        content(contentModuleName, ModuleLoadingRuleValue.REQUIRED)
      }
    }

    val result = contentModuleResult(
      moduleName = contentModuleName,
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = setOf(PluginId("owner.plugin")),
    )

    val model = testGenerationModel(graph)
    val errors = runBlocking {
      runValidationRule(
        ContentModulePluginDependencyValidator,
        model,
        slotOverrides = mapOf(Slots.CONTENT_MODULE to ContentModuleOutput(files = listOf(result))),
      )
    }

    assertThat(errors).isEmpty()
  }

  private fun contentModuleResult(
    moduleName: String,
    writtenPluginDependencies: List<PluginId>,
    allJpsPluginDependencies: Set<PluginId>,
  ): DependencyFileResult {
    return DependencyFileResult(
      contentModuleName = ContentModuleName(moduleName),
      descriptorPath = Path.of("$moduleName.xml").toAbsolutePath(),
      status = FileChangeStatus.UNCHANGED,
      writtenDependencies = emptyList(),
      writtenPluginDependencies = writtenPluginDependencies,
      allJpsPluginDependencies = allJpsPluginDependencies,
    )
  }
}
