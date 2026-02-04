// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraph
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.MissingTestPluginPluginDependencyError
import org.jetbrains.intellij.build.productLayout.pipeline.ContentModuleOutput
import org.jetbrains.intellij.build.productLayout.pipeline.Slots
import org.jetbrains.intellij.build.productLayout.productModules
import org.jetbrains.intellij.build.productLayout.stats.DependencyFileResult
import org.jetbrains.intellij.build.productLayout.stats.FileChangeStatus
import org.jetbrains.intellij.build.productLayout.util.DeferredFileUpdater
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@ExtendWith(TestFailureLogger::class)
class TestPluginPluginDependencyValidatorTest {
  @Test
  fun `missing plugin dependency in test plugin XML reports error`(@TempDir tempDir: Path) {
    val graph = pluginGraph {
      product("TestProduct") { bundlesPlugin("dep.plugin") }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
        content("dep.module")
      }
      testPlugin("test.plugin") {
        pluginId("test.plugin")
        content("consumer.module")
      }
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("test.plugin"),
      name = "Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        module("consumer.module")
      }
    )

    writePluginXml(tempDir, spec.pluginXmlPath, pluginXml("test.plugin"))

    val model = testGenerationModel(graph, fileUpdater = DeferredFileUpdater(tempDir)).copy(
      projectRoot = tempDir,
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
    )

    val result = dependencyResult("consumer.module", listOf("dep.module"))
    val errors = runBlocking {
      runValidationRule(
        TestPluginPluginDependencyValidator,
        model,
        slotOverrides = mapOf(Slots.CONTENT_MODULE to ContentModuleOutput(files = listOf(result))),
      )
    }

    val missingErrors = errors.filterIsInstance<MissingTestPluginPluginDependencyError>()
    assertThat(missingErrors).hasSize(1)
    assertThat(missingErrors.first().missingPluginIds).containsExactly(PluginId("dep.plugin"))
  }

  @Test
  fun `declared plugin dependency suppresses error`(@TempDir tempDir: Path) {
    val graph = pluginGraph {
      product("TestProduct") { bundlesPlugin("dep.plugin") }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
        content("dep.module")
      }
      testPlugin("test.plugin") {
        pluginId("test.plugin")
        content("consumer.module")
      }
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("test.plugin"),
      name = "Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        module("consumer.module")
      }
    )

    writePluginXml(tempDir, spec.pluginXmlPath, pluginXml("test.plugin", pluginDeps = listOf("dep.plugin")))

    val model = testGenerationModel(graph, fileUpdater = DeferredFileUpdater(tempDir)).copy(
      projectRoot = tempDir,
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
    )

    val result = dependencyResult("consumer.module", listOf("dep.module"))
    val errors = runBlocking {
      runValidationRule(
        TestPluginPluginDependencyValidator,
        model,
        slotOverrides = mapOf(Slots.CONTENT_MODULE to ContentModuleOutput(files = listOf(result))),
      )
    }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `module allowedMissing suppresses error`(@TempDir tempDir: Path) {
    val graph = pluginGraph {
      product("TestProduct") { bundlesPlugin("dep.plugin") }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
        content("dep.module")
      }
      testPlugin("test.plugin") {
        pluginId("test.plugin")
        content("consumer.module")
      }
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("test.plugin"),
      name = "Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        module("consumer.module", allowedMissingPluginIds = listOf("dep.plugin"))
      }
    )

    writePluginXml(tempDir, spec.pluginXmlPath, pluginXml("test.plugin"))

    val model = testGenerationModel(graph, fileUpdater = DeferredFileUpdater(tempDir)).copy(
      projectRoot = tempDir,
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
    )

    val result = dependencyResult("consumer.module", listOf("dep.module"))
    val errors = runBlocking {
      runValidationRule(
        TestPluginPluginDependencyValidator,
        model,
        slotOverrides = mapOf(Slots.CONTENT_MODULE to ContentModuleOutput(files = listOf(result))),
      )
    }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `auto-added module inherits allowedMissing from root`(@TempDir tempDir: Path) {
    val graph = pluginGraph {
      product("TestProduct") { bundlesPlugin("dep.plugin") }
      plugin("dep.plugin") {
        pluginId("dep.plugin")
        content("dep.module")
      }
      testPlugin("test.plugin") {
        pluginId("test.plugin")
        content("root.module")
        content("auto.module")
      }
    }

    val spec = TestPluginSpec(
      pluginId = PluginId("test.plugin"),
      name = "Test Plugin",
      pluginXmlPath = "test-plugin/META-INF/plugin.xml",
      spec = productModules {
        module("root.module", allowedMissingPluginIds = listOf("dep.plugin"))
        module("auto.module")
      }
    )

    writePluginXml(tempDir, spec.pluginXmlPath, pluginXml("test.plugin"))

    val dependencyChains = mapOf(
      spec.pluginId to mapOf(
        ContentModuleName("auto.module") to listOf(
          ContentModuleName("root.module"),
          ContentModuleName("auto.module"),
        )
      )
    )

    val model = testGenerationModel(graph, fileUpdater = DeferredFileUpdater(tempDir)).copy(
      projectRoot = tempDir,
      dslTestPluginsByProduct = mapOf("TestProduct" to listOf(spec)),
      dslTestPluginDependencyChains = dependencyChains,
    )

    val result = dependencyResult("auto.module", listOf("dep.module"))
    val errors = runBlocking {
      runValidationRule(
        TestPluginPluginDependencyValidator,
        model,
        slotOverrides = mapOf(Slots.CONTENT_MODULE to ContentModuleOutput(files = listOf(result))),
      )
    }

    assertThat(errors).isEmpty()
  }

  private fun dependencyResult(moduleName: String, testDependencies: List<String>): DependencyFileResult {
    return DependencyFileResult(
      contentModuleName = ContentModuleName(moduleName),
      descriptorPath = Path.of("$moduleName.xml").toAbsolutePath(),
      status = FileChangeStatus.UNCHANGED,
      writtenDependencies = emptyList(),
      testDependencies = testDependencies.map(::ContentModuleName),
    )
  }

  private fun writePluginXml(projectRoot: Path, relativePath: String, content: String) {
    val path = projectRoot.resolve(relativePath)
    Files.createDirectories(path.parent)
    Files.writeString(path, content)
  }

  private fun pluginXml(pluginId: String, pluginDeps: List<String> = emptyList()): String {
    val depsBlock = if (pluginDeps.isEmpty()) "" else buildString {
      append("\n  <dependencies>\n")
      for (dep in pluginDeps.sorted()) {
        append("    <plugin id=\"").append(dep).append("\"/>\n")
      }
      append("  </dependencies>\n")
    }
    return """
      <idea-plugin>
        <id>$pluginId</id>
        <name>Test Plugin</name>
        <vendor>JetBrains</vendor>$depsBlock
      </idea-plugin>
    """.trimIndent()
  }
}
