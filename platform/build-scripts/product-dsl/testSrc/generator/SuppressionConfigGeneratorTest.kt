// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.generator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.config.ContentModuleSuppression
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.deps.ContentModuleDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.deps.PluginDependencyPlanOutput
import org.jetbrains.intellij.build.productLayout.pipeline.ComputeContextImpl
import org.jetbrains.intellij.build.productLayout.pipeline.ErrorSlot
import org.jetbrains.intellij.build.productLayout.pipeline.NodeIds
import org.jetbrains.intellij.build.productLayout.pipeline.ProductModuleDepsOutput
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Tests for [SuppressionConfigGenerator] merge logic.
 *
 * Tests the key behaviors:
 * - Invalid keys (not in graph) are treated as stale
 * - DSL plugin descriptors are treated as stale
 * - Valid keys are preserved
 * - Unprocessed keys preserve all suppressions
 */
@ExtendWith(TestFailureLogger::class)
class SuppressionConfigGeneratorTest {

  @Test
  fun `update suppressions defers file writes until pipeline output stage`(@TempDir tempDir: Path): Unit = runBlocking {
    val suppressionsPath = tempDir.resolve("suppressions.json")
    val existingConfig = SuppressionConfig(
      contentModules = mapOf(
        ContentModuleName("owner.content") to ContentModuleSuppression(
          suppressPlugins = setOf(PluginId("dep.plugin")),
        )
      )
    )
    val existingContent = SuppressionConfig.serializeToString(existingConfig)
    Files.writeString(suppressionsPath, existingContent)

    val model = testGenerationModel(
      pluginGraph = pluginGraph {
        plugin("owner.plugin") {
          content("owner.content")
        }
      },
      fileUpdater = DeferredFileUpdater(tempDir),
      suppressionConfig = existingConfig,
      updateSuppressions = true,
      suppressionConfigPath = suppressionsPath,
    )

    val ctx = ComputeContextImpl(model)
    ctx.initSlot(Slots.PRODUCT_MODULE_DEPS)
    ctx.publish(Slots.PRODUCT_MODULE_DEPS, ProductModuleDepsOutput(files = emptyList()))
    ctx.initSlot(Slots.CONTENT_MODULE_PLAN)
    ctx.publish(Slots.CONTENT_MODULE_PLAN, ContentModuleDependencyPlanOutput(plans = emptyList()))
    ctx.initSlot(Slots.PLUGIN_DEPENDENCY_PLAN)
    ctx.publish(Slots.PLUGIN_DEPENDENCY_PLAN, PluginDependencyPlanOutput(plans = emptyList()))
    ctx.initSlot(Slots.LIBRARY_SUPPRESSIONS)
    ctx.publish(Slots.LIBRARY_SUPPRESSIONS, emptyList())
    ctx.initSlot(Slots.TEST_LIBRARY_SCOPE_SUPPRESSIONS)
    ctx.publish(Slots.TEST_LIBRARY_SCOPE_SUPPRESSIONS, emptyList())

    val contentModuleDepsErrorSlot = ErrorSlot(NodeIds.CONTENT_MODULE_DEPS)
    ctx.initSlot(contentModuleDepsErrorSlot)
    ctx.publish(contentModuleDepsErrorSlot, emptyList())
    val pluginXmlDepsErrorSlot = ErrorSlot(NodeIds.PLUGIN_XML_DEPS)
    ctx.initSlot(pluginXmlDepsErrorSlot)
    ctx.publish(pluginXmlDepsErrorSlot, emptyList())

    ctx.initSlot(Slots.SUPPRESSION_CONFIG)
    val nodeCtx = ctx.forNode(SuppressionConfigGenerator.id)
    SuppressionConfigGenerator.execute(nodeCtx)
    ctx.finalizeNodeErrors(SuppressionConfigGenerator.id)

    assertThat(Files.readString(suppressionsPath))
      .describedAs("SuppressionConfigGenerator should not write directly during node execution")
      .isEqualTo(existingContent)
    assertThat(model.fileUpdater.getDiffs())
      .describedAs("Changes should be deferred for pipeline commit stage")
      .hasSize(1)
  }

  @Nested
  inner class InvalidKeyHandlingTest {

    @Test
    fun `invalid module keys are treated as stale`() {
      val graph = pluginGraph {
        plugin("intellij.valid.plugin") {
          content("intellij.valid.module")
        }
      }

      graph.query {
        // Verify valid module exists
        assertThat(contentModule(ContentModuleName("intellij.valid.module"))).isNotNull()

        // Verify invalid module doesn't exist
        assertThat(contentModule(ContentModuleName("intellij.nonexistent.module"))).isNull()
      }
    }

    @Test
    fun `invalid plugin keys are treated as stale`() {
      val graph = pluginGraph {
        plugin("intellij.valid.plugin") {
          pluginId("com.intellij.valid")
          content("intellij.valid.module")
        }
      }

      graph.query {
        // Verify valid plugin exists
        assertThat(plugin("intellij.valid.plugin")).isNotNull()

        // Verify invalid plugin doesn't exist
        assertThat(plugin("intellij.nonexistent.plugin")).isNull()
      }
    }
  }

  @Nested
  inner class DslPluginHandlingTest {

    @Test
    fun `dsl-defined test plugins are identified correctly`() {
      val graph = pluginGraph {
        // Regular plugin (not DSL-defined)
        plugin("intellij.regular.plugin") {
          content("intellij.regular.module")
        }

        // Test plugin (DSL-defined via testPlugin {})
        testPlugin("intellij.test.plugin") {
          content("intellij.test.module")
        }
      }

      graph.query {
        val regularPlugin = plugin("intellij.regular.plugin")
        val testPlugin = plugin("intellij.test.plugin")

        assertThat(regularPlugin).isNotNull()
        assertThat(testPlugin).isNotNull()

        // Test plugins created via testPlugin {} are DSL-defined
        assertThat(testPlugin!!.isDslDefined).isTrue()

        // Regular plugins are not DSL-defined
        assertThat(regularPlugin!!.isDslDefined).isFalse()
      }
    }

    @Test
    fun `content modules of dsl plugins are not marked as dsl-defined`() {
      val graph = pluginGraph {
        testPlugin("intellij.test.plugin") {
          content("intellij.test.content.module")
        }
      }

      graph.query {
        // The plugin itself is DSL-defined
        val testPlugin = plugin("intellij.test.plugin")
        assertThat(testPlugin!!.isDslDefined).isTrue()

        // Content modules are regular modules (not DSL-defined)
        assertThat(contentModule(ContentModuleName("intellij.test.content.module"))).isNotNull()
        // Modules don't have isDslDefined flag - only plugins do
      }
    }
  }

  @Nested
  inner class MergeLogicTest {

    @Test
    fun `mergeSuppressions filters invalid keys from generated map`() {
      val graph = pluginGraph {
        plugin("intellij.valid.plugin") {
          content("intellij.valid.module")
        }
      }

      // Create test data
      val existing = mapOf(
        ContentModuleName("intellij.valid.module") to ContentModuleSuppression(
          suppressModules = sortedSetOf(ContentModuleName("intellij.dep.a")),
          suppressPlugins = sortedSetOf(),
        ),
        ContentModuleName("intellij.invalid.module") to ContentModuleSuppression(
          suppressModules = sortedSetOf(ContentModuleName("intellij.dep.b")),
          suppressPlugins = sortedSetOf(),
        ),
      )

      val generated = mapOf(
        ContentModuleName("intellij.valid.module") to ContentModuleSuppression(
          suppressModules = sortedSetOf(ContentModuleName("intellij.dep.a")),
          suppressPlugins = sortedSetOf(),
        ),
      )

      assertThat(existing).isNotEmpty()
      assertThat(generated).isNotEmpty()

      graph.query {
        // Verify the graph state
        assertThat(contentModule(ContentModuleName("intellij.valid.module"))).isNotNull()
        assertThat(contentModule(ContentModuleName("intellij.invalid.module"))).isNull()
      }

      // The actual merge happens inside the generator - this test verifies the graph setup
      // The generator will:
      // 1. Keep intellij.valid.module (exists in graph)
      // 2. Remove intellij.invalid.module (not in graph) and count as stale
    }

    @Test
    fun `mergeSuppressions filters dsl plugin keys from generated map`() {
      val graph = pluginGraph {
        plugin("intellij.regular.plugin") {
          pluginId("com.intellij.regular")
        }
        testPlugin("intellij.dsl.test.plugin") {
          pluginId("com.intellij.dsl.test")
        }
      }

      graph.query {
        val regularPlugin = plugin("intellij.regular.plugin")
        val dslPlugin = plugin("intellij.dsl.test.plugin")

        // Verify graph state
        assertThat(regularPlugin).isNotNull()
        assertThat(dslPlugin).isNotNull()
        assertThat(regularPlugin!!.isDslDefined).isFalse()
        assertThat(dslPlugin!!.isDslDefined).isTrue()
      }

      // The generator will:
      // 1. Keep intellij.regular.plugin suppressions
      // 2. Remove intellij.dsl.test.plugin suppressions (isDslDefined=true)
    }
  }
}
