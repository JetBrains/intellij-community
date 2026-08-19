// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.EDGE_BUNDLES
import com.intellij.platform.pluginGraph.PluginId
import com.intellij.platform.pluginGraph.PluginModuleId
import com.intellij.platform.pluginGraph.TargetName
import com.intellij.platform.pluginSystem.parser.impl.elements.ModuleLoadingRuleValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.graph.PluginGraphBuilder
import org.jetbrains.intellij.build.productLayout.model.error.PluginDescriptorIdConflictError
import org.jetbrains.intellij.build.productLayout.productModules
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(TestFailureLogger::class)
class PluginDescriptorIdConflictValidatorTest {
  @Test
  fun `reports conflicts between production and test descriptor ids`(): Unit = runBlocking(Dispatchers.Default) {
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
    val errors = runValidationRule(PluginDescriptorIdConflictValidator, model)

    val conflictErrors = errors.filterIsInstance<PluginDescriptorIdConflictError>()
    assertThat(conflictErrors).hasSize(1)

    val owners = conflictErrors.single().duplicates[PluginId("intellij.pycharm.pro.customizationJupyter")]
    assertThat(owners).isNotNull
    assertThat(owners!!.map { it.pluginName.value })
      .containsExactlyInAnyOrder("prod.plugin", "test.plugin")
    assertThat(owners.any { it.isTestPlugin }).isTrue()
  }

  @Test
  fun `ignores distinct production and test descriptor ids`(): Unit = runBlocking(Dispatchers.Default) {
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
    val errors = runValidationRule(PluginDescriptorIdConflictValidator, model)

    assertThat(errors).isEmpty()
  }

  @Test
  fun `reports conflicts with plugins bundled only via additionalBundledPluginTargetNames`(): Unit = runBlocking(Dispatchers.Default) {
    // 'extra.plugin' is not bundled by the product - it reaches the run only through the test plugin's
    // additionalBundledPluginTargetNames, mirroring '-Dadditional.modules=' of the intellij.yaml runner.
    val graph = pluginGraph {
      product("IDEA") {
        bundlesTestPlugin("test.plugin")
      }
      plugin("extra.plugin") {
        pluginId("com.example.extra")
        content("intellij.libraries.objenesis")
      }
      testPlugin("test.plugin") {
        pluginId("intellij.python.pro.rust.tests.plugin")
        content("intellij.libraries.objenesis")
      }
    }

    val model = testGenerationModel(graph, dslTestPluginsByProduct = testPluginSpecsWithExtraBundle())
    val errors = runValidationRule(PluginDescriptorIdConflictValidator, model)

    val conflictErrors = errors.filterIsInstance<PluginDescriptorIdConflictError>()
    assertThat(conflictErrors).hasSize(1)

    val owners = conflictErrors.single().duplicates[PluginId("intellij.libraries.objenesis")]
    assertThat(owners).isNotNull
    assertThat(owners!!.map { it.pluginName.value }).containsExactlyInAnyOrder("extra.plugin", "test.plugin")
  }

  @Test
  fun `ignores modules the test plugin declares without a namespace`(): Unit = runBlocking(Dispatchers.Default) {
    // A namespace-less content module is registered in the plugin's implicit namespace, so its runtime id differs
    // from the jetbrains-namespace one and the two never clash.
    val graph = pluginGraph {
      product("IDEA") {
        bundlesTestPlugin("test.plugin")
      }
      plugin("extra.plugin") {
        pluginId("com.example.extra")
        content("intellij.libraries.objenesis")
      }
      testPlugin("test.plugin") {
        pluginId("intellij.python.pro.rust.tests.plugin")
        content("intellij.libraries.objenesis", namespace = null)
      }
    }

    val model = testGenerationModel(graph, dslTestPluginsByProduct = testPluginSpecsWithExtraBundle())
    val errors = runValidationRule(PluginDescriptorIdConflictValidator, model)

    assertThat(errors).isEmpty()
  }

  @Test
  fun `ignores bundled alias nodes when checking descriptor id conflicts`(): Unit = runBlocking(Dispatchers.Default) {
    val builder = PluginGraphBuilder()
    val prodPlugin = TargetName("prod.plugin")
    val testPlugin = TargetName("test.plugin")
    val aliasId = PluginId("com.example.alias")

    builder.addPlugin(prodPlugin, isTest = false, pluginId = PluginId("com.example.prod"))
    builder.linkPluginMainTarget(prodPlugin)
    builder.linkPluginContent(
      pluginName = prodPlugin,
      pluginModuleId = PluginModuleId("intellij.prod.module", namespace = "jetbrains"),
      loadingMode = ModuleLoadingRuleValue.OPTIONAL,
      isTest = false,
    )

    builder.addPlugin(testPlugin, isTest = true, pluginId = aliasId)
    builder.linkPluginMainTarget(testPlugin)
    builder.linkPluginContent(
      pluginName = testPlugin,
      pluginModuleId = PluginModuleId("intellij.test.module", namespace = "jetbrains"),
      loadingMode = ModuleLoadingRuleValue.OPTIONAL,
      isTest = true,
    )

    builder.linkProductBundlesPlugin("IDEA", prodPlugin, isTest = false)
    builder.linkProductBundlesPlugin("IDEA", testPlugin, isTest = true)
    val aliasNodeId = builder.addAliasPlugin(aliasId)
    builder.addEdge(builder.addProduct("IDEA"), aliasNodeId, EDGE_BUNDLES)

    val model = testGenerationModel(builder.build())
    val errors = runValidationRule(PluginDescriptorIdConflictValidator, model)

    assertThat(errors).isEmpty()
  }
}

/** Declares 'extra.plugin' as a plugin that 'test.plugin' pulls into the run without the product bundling it. */
private fun testPluginSpecsWithExtraBundle(): Map<String, List<TestPluginSpec>> {
  return mapOf(
    "IDEA" to listOf(
      TestPluginSpec(
        pluginId = PluginId("intellij.python.pro.rust.tests.plugin"),
        name = "Test Plugin",
        pluginXmlPath = "test-plugin/META-INF/plugin.xml",
        spec = productModules {},
        additionalBundledPluginTargetNames = listOf(TargetName("extra.plugin")),
      )
    )
  )
}
