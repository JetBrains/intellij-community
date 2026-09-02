// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.validator

import com.intellij.platform.pluginGraph.ContentModuleName
import com.intellij.platform.pluginGraph.PluginId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.TestFailureLogger
import org.jetbrains.intellij.build.productLayout.config.ContentModuleSuppression
import org.jetbrains.intellij.build.productLayout.config.SuppressionConfig
import org.jetbrains.intellij.build.productLayout.dependency.ModuleDescriptorCache
import org.jetbrains.intellij.build.productLayout.dependency.TestPluginGraphBuilder
import org.jetbrains.intellij.build.productLayout.dependency.jpsProject
import org.jetbrains.intellij.build.productLayout.dependency.pluginGraphWithDescriptors
import org.jetbrains.intellij.build.productLayout.dependency.runValidationRule
import org.jetbrains.intellij.build.productLayout.dependency.testGenerationModel
import org.jetbrains.intellij.build.productLayout.model.error.ContentModuleDependencyDeclarationError
import org.jetbrains.intellij.build.productLayout.model.error.ContentModuleDependencyProblemKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for [ContentModuleDependencyDeclarationValidator].
 *
 * Each test writes a real descriptor, because the rule reads the text of the file and not the graph edges.
 */
@ExtendWith(TestFailureLogger::class)
class ContentModuleDependencyDeclarationValidatorTest {
  @Test
  fun `reports the old Java plugin alias`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf("com.example.foo.impl" to descriptor(pluginDependencies = listOf("com.intellij.modules.java"))),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      linkPluginMainTarget("com.example.foo.plugin")
    }

    assertThat(kinds(errors)).containsExactly(ContentModuleDependencyProblemKind.JAVA_MODULE_ALIAS)
  }

  @Test
  fun `reports a platform dependency next to a module dependency`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf(
        "com.example.foo.impl" to descriptor(
          pluginDependencies = listOf("com.intellij.modules.platform"),
          moduleDependencies = listOf("com.example.foo.core"),
        ),
        "com.example.foo.core" to descriptor(),
      ),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
        content("com.example.foo.core")
      }
      linkPluginMainTarget("com.example.foo.plugin")
    }

    assertThat(kinds(errors)).containsExactly(ContentModuleDependencyProblemKind.REDUNDANT_PLATFORM_DEPENDENCY)
  }

  @Test
  fun `accepts a lone platform dependency`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf("com.example.foo.impl" to descriptor(pluginDependencies = listOf("com.intellij.modules.platform"))),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      linkPluginMainTarget("com.example.foo.plugin")
    }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `reports a plugin id that no plugin defines`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf("com.example.foo.impl" to descriptor(pluginDependencies = listOf("com.example.gone"))),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      linkPluginMainTarget("com.example.foo.plugin")
    }

    assertThat(kinds(errors)).containsExactly(ContentModuleDependencyProblemKind.UNRESOLVED_PLUGIN)
  }

  @Test
  fun `accepts a plugin id that another plugin defines`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf("com.example.foo.impl" to descriptor(pluginDependencies = listOf("com.example.bar"))),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      plugin("com.example.bar.plugin") {
        pluginId("com.example.bar")
      }
      linkPluginMainTarget("com.example.foo.plugin")
      linkPluginMainTarget("com.example.bar.plugin")
    }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `accepts a plugin id that a descriptor alias defines`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf(
        "com.example.foo.impl" to descriptor(pluginDependencies = listOf("com.example.alias")),
        "com.example.bar.impl" to descriptor(aliases = listOf("com.example.alias")),
      ),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      plugin("com.example.bar.plugin") {
        pluginId("com.example.bar")
        content("com.example.bar.impl")
      }
      linkPluginMainTarget("com.example.foo.plugin")
      linkPluginMainTarget("com.example.bar.plugin")
    }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `accepts an OS requirement id`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf("com.example.foo.impl" to descriptor(pluginDependencies = listOf("com.intellij.modules.os.mac"))),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      linkPluginMainTarget("com.example.foo.plugin")
    }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `accepts a plugin id that suppressions allow`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf("com.example.foo.impl" to descriptor(pluginDependencies = listOf("com.example.external"))),
      suppressionConfig = SuppressionConfig(contentModules = mapOf(
        ContentModuleName("com.example.foo.impl") to ContentModuleSuppression(suppressPlugins = setOf(PluginId("com.example.external"))),
      )),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      linkPluginMainTarget("com.example.foo.plugin")
    }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `reports a plugin id that the descriptor declares two times`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf("com.example.foo.impl" to descriptor(pluginDependencies = listOf("com.example.bar", "com.example.bar"))),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      plugin("com.example.bar.plugin") {
        pluginId("com.example.bar")
      }
      linkPluginMainTarget("com.example.foo.plugin")
      linkPluginMainTarget("com.example.bar.plugin")
    }

    assertThat(kinds(errors)).containsExactly(ContentModuleDependencyProblemKind.DUPLICATE_PLUGIN)
  }

  @Test
  fun `checks an element that follows a K1 dependency`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf(
        "com.example.foo.impl" to descriptor(pluginDependencies = listOf("com.intellij.modules.kotlin.k1", "com.example.gone")),
      ),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      linkPluginMainTarget("com.example.foo.plugin")
    }

    assertThat(kinds(errors)).containsExactly(ContentModuleDependencyProblemKind.UNRESOLVED_PLUGIN)
  }

  @Test
  fun `accepts a dependency on the id of the owning plugin`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf("com.example.foo.impl" to descriptor(pluginDependencies = listOf("com.example.foo"))),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      linkPluginMainTarget("com.example.foo.plugin")
    }

    assertThat(errors).isEmpty()
  }

  @Test
  fun `reports a module dependency that names a plugin main module`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf("com.example.foo.impl" to descriptor(moduleDependencies = listOf("com.example.bar.plugin"))),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl")
      }
      plugin("com.example.bar.plugin") {
        pluginId("com.example.bar")
      }
      linkPluginMainTarget("com.example.foo.plugin")
      linkPluginMainTarget("com.example.bar.plugin")
    }

    assertThat(kinds(errors)).containsExactly(ContentModuleDependencyProblemKind.PLUGIN_AS_MODULE)
    assertThat(errors.single().problems.single().fix).isEqualTo("<plugin id=\"com.example.bar\"/>")
  }

  @Test
  fun `reports an internal module dependency from another namespace`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf(
        "com.example.foo.impl" to descriptor(moduleDependencies = listOf("com.example.bar.impl")),
        "com.example.bar.impl" to descriptor(visibility = "internal"),
      ),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl", namespace = "foo")
      }
      plugin("com.example.bar.plugin") {
        pluginId("com.example.bar")
        content("com.example.bar.impl", namespace = "bar")
      }
      linkPluginMainTarget("com.example.foo.plugin")
      linkPluginMainTarget("com.example.bar.plugin")
    }

    assertThat(kinds(errors)).containsExactly(ContentModuleDependencyProblemKind.INTERNAL_FROM_OTHER_NAMESPACE)
  }

  @Test
  fun `accepts an internal module dependency in one namespace`(@TempDir tempDir: Path): Unit = runBlocking(Dispatchers.Default) {
    val errors = validate(
      tempDir = tempDir,
      descriptors = mapOf(
        "com.example.foo.impl" to descriptor(moduleDependencies = listOf("com.example.bar.impl")),
        "com.example.bar.impl" to descriptor(visibility = "internal"),
      ),
    ) {
      plugin("com.example.foo.plugin") {
        pluginId("com.example.foo")
        content("com.example.foo.impl", namespace = "shared")
      }
      plugin("com.example.bar.plugin") {
        pluginId("com.example.bar")
        content("com.example.bar.impl", namespace = "shared")
      }
      linkPluginMainTarget("com.example.foo.plugin")
      linkPluginMainTarget("com.example.bar.plugin")
    }

    assertThat(errors).isEmpty()
  }
}

private fun descriptor(
  pluginDependencies: List<String> = emptyList(),
  moduleDependencies: List<String> = emptyList(),
  aliases: List<String> = emptyList(),
  visibility: String? = null,
): String {
  val root = if (visibility == null) "<idea-plugin>" else "<idea-plugin visibility=\"$visibility\">"
  return buildString {
    appendLine(root)
    for (alias in aliases) {
      appendLine("  <module value=\"$alias\"/>")
    }
    if (pluginDependencies.isNotEmpty() || moduleDependencies.isNotEmpty()) {
      appendLine("  <dependencies>")
      for (id in pluginDependencies) {
        appendLine("    <plugin id=\"$id\"/>")
      }
      for (name in moduleDependencies) {
        appendLine("    <module name=\"$name\"/>")
      }
      appendLine("  </dependencies>")
    }
    appendLine("</idea-plugin>")
  }
}

/**
 * Writes each descriptor into the resource root of its own JPS module, then runs the rule on the built graph.
 */
private suspend fun validate(
  tempDir: Path,
  descriptors: Map<String, String>,
  suppressionConfig: SuppressionConfig = SuppressionConfig(),
  graphBlock: TestPluginGraphBuilder.() -> Unit,
): List<ContentModuleDependencyDeclarationError> {
  val jps = jpsProject(tempDir) {
    for (name in descriptors.keys) {
      module(name) {
        resourceRoot()
      }
    }
  }
  for ((name, content) in descriptors) {
    val resourceDir = tempDir.resolve(name.replace('.', '/')).resolve("resources")
    Files.createDirectories(resourceDir)
    Files.writeString(resourceDir.resolve("$name.xml"), content)
  }

  val graph = pluginGraphWithDescriptors(ModuleDescriptorCache(jps.outputProvider), graphBlock)
  val model = testGenerationModel(graph, outputProvider = jps.outputProvider, suppressionConfig = suppressionConfig)
  val errors = runValidationRule(ContentModuleDependencyDeclarationValidator, model)
  return errors.map { it as ContentModuleDependencyDeclarationError }
}

private fun kinds(errors: List<ContentModuleDependencyDeclarationError>): List<ContentModuleDependencyProblemKind> {
  return errors.flatMap { error -> error.problems.map { it.kind } }
}
