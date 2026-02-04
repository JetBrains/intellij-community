// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.dependency

import com.intellij.platform.pluginGraph.TargetName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.discovery.PluginContentInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Tests for plugin.xml generation from JPS dependencies.
 *
 * These tests verify that:
 * - JPS dependency on a plugin module (has META-INF/plugin.xml) → `<plugin id="..."/>` in plugin.xml
 * - JPS dependency on a content module (has {moduleName}.xml) → `<module name="..."/>` in dependencies
 * - Both production and test plugins auto-derive plugin dependencies from JPS
 */
@ExtendWith(TestFailureLogger::class)
class PluginXmlGenerationTest {
  @Test
  fun `test plugin with JPS dependency on plugin module generates plugin element in plugin xml`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        // Target plugin that will be depended on
        plugin("intellij.target.plugin") {
          pluginId = "intellij.target.plugin"
          content("intellij.target.module")
        }
        contentModule("intellij.target.module") {
          descriptor = """<idea-plugin package="com.intellij.target"/>"""
        }
        // Test plugin (isTestPlugin=true) with JPS dep on target plugin
        plugin("intellij.consumer.test.plugin") {
          isTestPlugin = true  // Test plugins get plugin deps auto-derived
          content("intellij.consumer.module")
          // JPS dep is simulated via content module's jpsDependency
        }
        contentModule("intellij.consumer.module") {
          descriptor = """<idea-plugin package="com.intellij.consumer"/>"""
          jpsDependency("intellij.target.plugin")  // JPS dep on plugin → <plugin id="..."/> in plugin.xml
        }
      }

      setup.generateDependencies(listOf("intellij.consumer.test.plugin", "intellij.target.plugin"))

      // Verify: TEST PLUGIN plugin.xml contains <plugin id="..."/> for the plugin dependency
      val diffs = setup.strategy.getDiffs()
      val pluginXmlDiff = diffs.find { it.path.toString().contains("plugin.xml") && it.path.toString().contains("consumer") }
      assertThat(pluginXmlDiff)
        .describedAs("Test plugin plugin.xml should be updated")
        .isNotNull()
      assertThat(pluginXmlDiff!!.expectedContent)
        .describedAs("Test plugin plugin.xml should have <plugin id=\"...\"/> for plugin dependency")
        .contains("<plugin id=\"intellij.target.plugin\"/>")
    }
  }
  @Test
  fun `JPS dependency on content module generates module element not plugin element`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        // Regular content module (not a plugin)
        contentModule("intellij.shared.content") {
          descriptor = """<idea-plugin package="com.intellij.shared"/>"""
        }
        // Plugin that contains a content module with JPS dep on another content module
        plugin("intellij.consumer.plugin") {
          content("intellij.consumer.module")
        }
        contentModule("intellij.consumer.module") {
          descriptor = """<idea-plugin package="com.intellij.consumer"/>"""
          jpsDependency("intellij.shared.content")  // JPS dep on content module → <module name="..."/>
        }
      }

      setup.generateDependencies(listOf("intellij.consumer.plugin"))

      // Verify: content module descriptor contains <module name="..."/> (not <plugin>)
      val diffs = setup.strategy.getDiffs()
      val consumerModuleDiff = diffs.find { it.path.toString().contains("intellij.consumer.module.xml") }
      assertThat(consumerModuleDiff)
        .describedAs("Content module descriptor should be updated")
        .isNotNull()
      assertThat(consumerModuleDiff!!.expectedContent)
        .describedAs("Content module should have <module name=\"...\"/> for content module dependency")
        .contains("<module name=\"intellij.shared.content\"/>")
      assertThat(consumerModuleDiff.expectedContent)
        .describedAs("Content module should NOT have <plugin> for content module dependency")
        .doesNotContain("<plugin id=\"intellij.shared.content\"/>")
    }
  }

  @Test
  fun `JPS dependency on content module with descriptor is treated as module not plugin`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
      // This tests the case where a module has BOTH a META-INF/plugin.xml AND a {moduleName}.xml descriptor.
      // Example: intellij.yaml is embedded in yaml plugin (has yaml's plugin.xml) but also has intellij.yaml.xml descriptor.
      // Such modules should be treated as content module dependencies, not plugin dependencies.

      val setup = pluginTestSetup(tempDir) {
        // Target plugin containing an embedded content module
        plugin("intellij.yaml.plugin") {
          pluginId = "org.jetbrains.plugins.yaml"
          content("intellij.yaml")
        }
        // The embedded content module has its own descriptor
        contentModule("intellij.yaml") {
          descriptor = """<idea-plugin package="org.jetbrains.yaml"/>"""
        }
        // Consumer plugin with JPS dep on the content module
        plugin("intellij.consumer.plugin") {
          content("intellij.consumer.module")
        }
        contentModule("intellij.consumer.module") {
          descriptor = """<idea-plugin package="com.intellij.consumer"/>"""
          jpsDependency("intellij.yaml")  // JPS dep on content module → should be <module>, not <plugin>
        }
      }

      // Also register intellij.yaml as a "plugin" in the cache to simulate shared resources dir
      // (in reality, pluginContentCache would return info because it finds the parent plugin's plugin.xml)
      val yamlPluginInfo = setup.pluginContentInfos["intellij.yaml.plugin"]!!
      val augmentedCache = object : PluginContentProvider {
        override suspend fun getOrExtract(pluginModule: TargetName): PluginContentInfo? {
          // Make intellij.yaml detectable as "plugin" (simulates shared resources dir with plugin.xml)
          if (pluginModule.value == "intellij.yaml") {
            return yamlPluginInfo  // Returns the parent plugin's info
          }
          return setup.pluginContentCache.getOrExtract(pluginModule)
        }
      }

      coroutineScope {
        val descriptorCache = ModuleDescriptorCache(setup.jps.outputProvider, this)
        generatePluginDependencies(
          plugins = listOf("intellij.consumer.plugin", "intellij.yaml.plugin"),
          pluginContentCache = augmentedCache,
          testSetup = setup,
          graph = setup.pluginGraph,
          descriptorCache = descriptorCache,
          suppressionConfig = SuppressionConfig(),
          strategy = setup.strategy,
          testFrameworkContentModules = emptySet(),
        )
      }

      // Verify: content module descriptor has <module name="..."/> NOT <plugin id="..."/>
      val diffs = setup.strategy.getDiffs()
      val consumerModuleDiff = diffs.find { it.path.toString().contains("intellij.consumer.module.xml") }
      assertThat(consumerModuleDiff)
        .describedAs("Content module descriptor should be updated")
        .isNotNull()
      assertThat(consumerModuleDiff!!.expectedContent)
        .describedAs("Should have <module name=\"intellij.yaml\"/> for content module with descriptor")
        .contains("<module name=\"intellij.yaml\"/>")
      assertThat(consumerModuleDiff.expectedContent)
        .describedAs("Should NOT have <plugin id=\"...\"/> for content module with descriptor")
        .doesNotContain("<plugin id=\"org.jetbrains.plugins.yaml\"/>")
    }
  @Test
  fun `production plugin with JPS dependency on plugin module generates plugin element`(@TempDir tempDir: Path) {
    runBlocking(Dispatchers.Default) {
      val setup = pluginTestSetup(tempDir) {
        // Target plugin that will be depended on
        plugin("intellij.target.plugin") {
          pluginId = "intellij.target.plugin"
          content("intellij.target.module")
        }
        contentModule("intellij.target.module") {
          descriptor = """<idea-plugin package="com.intellij.target"/>"""
        }
        // Production plugin (isTestPlugin=false, the default) with JPS dep on target plugin
        plugin("intellij.consumer.plugin") {
          // isTestPlugin = false (default) - production plugins also get plugin deps auto-derived
          content("intellij.consumer.module")
        }
        contentModule("intellij.consumer.module") {
          descriptor = """<idea-plugin package="com.intellij.consumer"/>"""
          jpsDependency("intellij.target.plugin")  // JPS dep on plugin → <plugin id="..."/> in plugin.xml
        }
      }

      setup.generateDependencies(listOf("intellij.consumer.plugin", "intellij.target.plugin"))

      // Verify: PRODUCTION PLUGIN plugin.xml contains <plugin id="..."/> for the plugin dependency
      val diffs = setup.strategy.getDiffs()
      val pluginXmlDiff = diffs.find { it.path.toString().contains("plugin.xml") && it.path.toString().contains("consumer") }
      assertThat(pluginXmlDiff)
        .describedAs("Production plugin plugin.xml should be updated")
        .isNotNull()
      assertThat(pluginXmlDiff!!.expectedContent)
        .describedAs("Production plugin plugin.xml should have <plugin id=\"...\"/> for plugin dependency")
        .contains("<plugin id=\"intellij.target.plugin\"/>")
    }
  }
}
