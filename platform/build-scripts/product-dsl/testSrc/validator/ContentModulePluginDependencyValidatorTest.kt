// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.config.ValidationException
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlan
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.model.error.MissingContentModulePluginDependencyError
import org.jetbrains.intellij.build.productLayout.moduleSet
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.productModules
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class ContentModulePluginDependencyValidatorTest {
  @Test
  fun `missing plugin dependency in XML reports error`(): Unit = runBlocking {
    val graph = pluginGraph {
      plugin("owner.plugin") {
        pluginId("owner.plugin")
        content("owner.content", ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
      }
    }

    val plan = contentModulePlan(
      moduleName = "owner.content",
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = setOf(PluginId("dep.plugin")),
    )

    val model = testGenerationModel(graph)
    val errors = runValidationRule(
      ContentModulePluginDependencyValidator,
      model,
      slotOverrides = mapOf(Slots.CONTENT_MODULE_PLAN to ContentModuleDependencyPlanOutput(plans = listOf(plan))),
    )

    val missingErrors = errors.filterIsInstance<MissingContentModulePluginDependencyError>()
    assertThat(missingErrors).hasSize(1)
    assertThat(missingErrors.first().missingPluginIds).containsExactly(PluginId("dep.plugin"))
    assertThat(missingErrors.first().proposedPatches)
      .anyMatch { it.title.contains("suppressions.json") }
  }

  @Test
  fun `containing plugin dependency is ignored`(): Unit = runBlocking {
    val contentModuleName = "owner.content.alt"
    val graph = pluginGraph {
      plugin("owner.plugin") {
        pluginId("owner.plugin")
        content(contentModuleName, ModuleLoadingRuleValue.REQUIRED)
      }
    }

    val plan = contentModulePlan(
      moduleName = contentModuleName,
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = setOf(PluginId("owner.plugin")),
    )

    val model = testGenerationModel(graph)
    val errors = runValidationRule(
      ContentModulePluginDependencyValidator,
      model,
      slotOverrides = mapOf(Slots.CONTENT_MODULE_PLAN to ContentModuleDependencyPlanOutput(plans = listOf(plan))),
    )

    assertThat(errors).isEmpty()
  }

  @Test
  fun `suppression config allowed missing suppresses error for regular module`(): Unit = runBlocking {
    val graph = pluginGraph {
      plugin("owner.plugin") {
        pluginId("owner.plugin")
        content("owner.content", ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
      }
    }

    val plan = contentModulePlan(
      moduleName = "owner.content",
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = setOf(PluginId("dep.plugin")),
    )

    val suppressionConfig = SuppressionConfig(
      validationExceptions = mapOf(
        ContentModuleName("owner.content") to ValidationException(allowMissingPlugins = setOf(PluginId("dep.plugin")))
      )
    )
    val model = testGenerationModel(graph, suppressionConfig = suppressionConfig)
    val errors = runValidationRule(
      ContentModulePluginDependencyValidator,
      model,
      slotOverrides = mapOf(Slots.CONTENT_MODULE_PLAN to ContentModuleDependencyPlanOutput(plans = listOf(plan))),
    )

    assertThat(errors).isEmpty()
  }

  @Test
  fun `pure dsl test module planner suppressions prevent hard error`(): Unit = runBlocking {
    val graph = pluginGraph {
      testPlugin("test.plugin") {
        pluginId("test.plugin")
        content("owner.content", ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
      }
    }

    val plan = contentModulePlan(
      moduleName = "owner.content",
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = setOf(PluginId("dep.plugin")),
      suppressedPlugins = setOf(PluginId("dep.plugin")),
    )

    val spec = TestPluginSpec(
      pluginId = PluginId("test.plugin"),
      name = "Test Plugin",
      pluginXmlPath = "test/META-INF/plugin.xml",
      spec = productModules {
        module("owner.content")
      },
    )
    val suppressionConfig = SuppressionConfig(
      validationExceptions = mapOf(
        ContentModuleName("owner.content") to ValidationException(allowMissingPlugins = setOf(PluginId("dep.plugin")))
      )
    )
    val model = testGenerationModel(graph, suppressionConfig = suppressionConfig).copy(
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec))
    )
    val errors = runValidationRule(
      ContentModulePluginDependencyValidator,
      model,
      slotOverrides = mapOf(Slots.CONTENT_MODULE_PLAN to ContentModuleDependencyPlanOutput(plans = listOf(plan))),
    )

    assertThat(errors).isEmpty()
  }

  @Test
  fun `pure dsl test module module-level allowedMissing suppresses error`(): Unit = runBlocking {
    val graph = pluginGraph {
      testPlugin("test.plugin") {
        pluginId("test.plugin")
        content("owner.content", ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
      }
    }

    val plan = contentModulePlan(
      moduleName = "owner.content",
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = setOf(PluginId("dep.plugin")),
    )

    val spec = TestPluginSpec(
      pluginId = PluginId("test.plugin"),
      name = "Test Plugin",
      pluginXmlPath = "test/META-INF/plugin.xml",
      spec = productModules {
        module("owner.content", allowedMissingPluginIds = listOf("dep.plugin"))
      },
    )
    val model = testGenerationModel(graph).copy(
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec))
    )
    val errors = runValidationRule(
      ContentModulePluginDependencyValidator,
      model,
      slotOverrides = mapOf(Slots.CONTENT_MODULE_PLAN to ContentModuleDependencyPlanOutput(plans = listOf(plan))),
    )

    assertThat(errors).isEmpty()
  }

  @Test
  fun `dsl declared module uses module-level allowedMissing even with mixed ownership`(): Unit = runBlocking {
    val graph = pluginGraph {
      testPlugin("test.plugin") {
        pluginId("test.plugin")
        content("shared.content", ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("owner.plugin") {
        pluginId("owner.plugin")
        content("shared.content", ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
      }
    }

    val plan = contentModulePlan(
      moduleName = "shared.content",
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = setOf(PluginId("dep.plugin")),
    )

    val spec = TestPluginSpec(
      pluginId = PluginId("test.plugin"),
      name = "Test Plugin",
      pluginXmlPath = "test/META-INF/plugin.xml",
      spec = productModules {
        module("shared.content", allowedMissingPluginIds = listOf("dep.plugin"))
      },
    )
    val model = testGenerationModel(graph).copy(
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec))
    )
    val errors = runValidationRule(
      ContentModulePluginDependencyValidator,
      model,
      slotOverrides = mapOf(Slots.CONTENT_MODULE_PLAN to ContentModuleDependencyPlanOutput(plans = listOf(plan))),
    )

    assertThat(errors).isEmpty()
  }

  @Test
  fun `dsl module in module set gets unified patch for allowedMissing`(): Unit = runBlocking {
    val graph = pluginGraph {
      testPlugin("test.plugin") {
        pluginId("test.plugin")
        content("intellij.platform.testFramework", ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
      }
    }

    val plan = contentModulePlan(
      moduleName = "intellij.platform.testFramework",
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = setOf(PluginId("dep.plugin")),
    )

    val sharedSet = moduleSet("testFrameworks") {
      module("intellij.platform.testFramework")
    }
    val spec = TestPluginSpec(
      pluginId = PluginId("test.plugin"),
      name = "Test Plugin",
      pluginXmlPath = "test/META-INF/plugin.xml",
      spec = productModules {
        moduleSet(sharedSet)
      },
    )

    val tempDir = Files.createTempDirectory("content-module-plugin-dep-validator")
    val moduleSetSource = tempDir.resolve("community/platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/validator/FakeModuleSetSource.kt")
    Files.createDirectories(moduleSetSource.parent)
    Files.writeString(
      moduleSetSource,
      """
      package org.jetbrains.intellij.build.productLayout.validator

      class FakeModuleSetSource {
        fun testFrameworks() {
          module("intellij.platform.testFramework")
        }
      }
      """.trimIndent() + "\n"
    )

    val baseModel = testGenerationModel(graph)
    val model = baseModel.copy(
      projectRoot = tempDir,
      discovery = baseModel.discovery.copy(
        moduleSetsByLabel = mapOf("community" to listOf(sharedSet)),
        moduleSetSources = mapOf("community" to (FakeModuleSetSource() to tempDir.resolve("unused"))),
      ),
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
    )

    val errors = runValidationRule(
      ContentModulePluginDependencyValidator,
      model,
      slotOverrides = mapOf(Slots.CONTENT_MODULE_PLAN to ContentModuleDependencyPlanOutput(plans = listOf(plan))),
    )

    val missingError = errors.filterIsInstance<MissingContentModulePluginDependencyError>().single()
    val dslPatch = missingError.proposedPatches.firstOrNull { it.title.contains("allowedMissingPluginIds") }

    assertThat(dslPatch).isNotNull
    assertThat(dslPatch!!.patch)
      .contains("--- a/community/platform/build-scripts/src/org/jetbrains/intellij/build/productLayout/validator/FakeModuleSetSource.kt")
    assertThat(dslPatch.patch)
      .contains("+    module(\"intellij.platform.testFramework\", allowedMissingPluginIds = listOf(\"dep.plugin\"))")
    assertThat(missingError.proposedPatches.map { it.title })
      .doesNotContain("Suppression snippet for DSL module declaration")
  }

  @Test
  fun `update suppressions mode suppresses regular module missing plugin deps from planner`(): Unit = runBlocking {
    val graph = pluginGraph {
      plugin("owner.plugin") {
        pluginId("owner.plugin")
        content("owner.content", ModuleLoadingRuleValue.REQUIRED)
      }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
      }
    }

    val plan = contentModulePlan(
      moduleName = "owner.content",
      writtenPluginDependencies = emptyList(),
      allJpsPluginDependencies = setOf(PluginId("dep.plugin")),
      suppressedPlugins = setOf(PluginId("dep.plugin")),
    )

    val model = testGenerationModel(
      pluginGraph = graph,
      updateSuppressions = true,
      suppressionConfigPath = Path.of("platform/buildScripts/suppressions.json"),
    )
    val errors = runValidationRule(
      ContentModulePluginDependencyValidator,
      model,
      slotOverrides = mapOf(Slots.CONTENT_MODULE_PLAN to ContentModuleDependencyPlanOutput(plans = listOf(plan))),
    )

    assertThat(errors).isEmpty()
  }

  private fun contentModulePlan(
    moduleName: String,
    writtenPluginDependencies: List<PluginId>,
    allJpsPluginDependencies: Set<PluginId>,
    suppressedPlugins: Set<PluginId> = emptySet(),
  ): ContentModuleDependencyPlan {
    return ContentModuleDependencyPlan(
      contentModuleName = ContentModuleName(moduleName),
      descriptorPath = Path.of("$moduleName.xml").toAbsolutePath(),
      descriptorContent = "",
      moduleDependencies = emptyList(),
      pluginDependencies = emptyList(),
      testDependencies = emptyList(),
      existingXmlModuleDependencies = emptySet(),
      existingXmlPluginDependencies = emptySet(),
      writtenPluginDependencies = writtenPluginDependencies,
      allJpsPluginDependencies = allJpsPluginDependencies,
      suppressedModules = emptySet(),
      suppressedPlugins = suppressedPlugins,
      suppressionUsages = emptyList(),
    )
  }
}

private class FakeModuleSetSource
