// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout.discovery

import com.intellij.platform.pluginGraph.TargetName
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.productLayout.dependency.createTestModuleOutputProvider
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText

class SyntheticTestPluginsTest {
  @TempDir
  lateinit var tempDir: Path

  private val project: JpsProject = JpsElementFactory.getInstance().createModel().project

  @Test
  fun `the placeholder plugin stands for every enabled module`() {
    testModule("intellij.sample.tests")
    testModule("intellij.sample.more.tests")
    testModule("intellij.other.tests")
    val runner = runner(
      additionalModules = listOf("$TEST_MODULE_NAME_PLACEHOLDER._test_plugin", "intellij.sample.extra"),
      enabledForModules = listOf("intellij.sample.*"),
      disabledForModules = listOf("intellij.sample.more.tests"),
    )

    val result = computeSyntheticTestPlugins(listOf(runner), bundledPluginsByProduct = mapOf("Sample" to listOf("intellij.sample.plugin")), outputProvider = createTestModuleOutputProvider(project), createMissingDescriptors = false)

    assertThat(result.problems).isEmpty()
    val spec = result.specsByProduct.getValue("Sample").single()
    assertThat(spec.pluginId.value).isEqualTo("intellij.sample.tests._test_plugin")
    assertThat(spec.devBuildSynthetic).isTrue()
    assertThat(spec.pluginXmlPath).isEmpty()
    assertThat(spec.spec.additionalModules.map { it.moduleId.name }).containsExactly("intellij.sample.tests")
    assertThat(spec.additionalBundledPluginTargetNames).containsExactly(TargetName("intellij.sample.plugin"), TargetName("intellij.sample.extra"))
  }

  @Test
  fun `an explicit entry point stands for its module, a runner without a synthetic plugin adds nothing`() {
    testModule("intellij.sample.tests")
    testModule("intellij.other.tests")
    val explicit = runner(additionalModules = listOf("intellij.other.tests._test_plugin"), enabledForModules = listOf("intellij.sample.tests"), entryPointModule = "intellij.other.tests")
    val handWritten = runner(additionalModules = listOf("intellij.sample.tests.plugin"), enabledForModules = listOf("intellij.sample.tests"))

    val result = computeSyntheticTestPlugins(listOf(explicit, handWritten), bundledPluginsByProduct = mapOf("Sample" to listOf("intellij.sample.plugin")), outputProvider = createTestModuleOutputProvider(project), createMissingDescriptors = false)

    assertThat(result.problems).isEmpty()
    assertThat(result.specsByProduct.getValue("Sample").map { it.pluginId.value }).containsExactly("intellij.other.tests._test_plugin")
  }

  @Test
  fun `an unknown product and a plugin that is not the entry point one are skipped, a missing module and a missing descriptor are reported`() {
    testModule("intellij.sample.tests", descriptor = false)
    val unknownProduct = runner(additionalModules = listOf("$TEST_MODULE_NAME_PLACEHOLDER._test_plugin"), enabledForModules = listOf("intellij.sample.tests"), platformPrefix = "Nowhere")
    val missingModule = runner(additionalModules = listOf("intellij.missing.tests._test_plugin"), enabledForModules = emptyList(), entryPointModule = "intellij.missing.tests")
    val missingDescriptor = runner(additionalModules = listOf("$TEST_MODULE_NAME_PLACEHOLDER._test_plugin"), enabledForModules = listOf("intellij.sample.tests"))
    val otherPlugin = runner(additionalModules = listOf("$TEST_MODULE_NAME_PLACEHOLDER._test_plugin"), enabledForModules = listOf("intellij.sample.tests"), entryPointModule = "intellij.sample.tests")

    val result = computeSyntheticTestPlugins(listOf(unknownProduct, missingModule, missingDescriptor, otherPlugin), bundledPluginsByProduct = mapOf("Sample" to listOf("intellij.sample.plugin")), outputProvider = createTestModuleOutputProvider(project), createMissingDescriptors = false)

    assertThat(result.specsByProduct).isEmpty()
    assertThat(result.problems.map { it.message }).satisfiesExactly(
      { assertThat(it).contains("'intellij.missing.tests' is not in the project") },
      { assertThat(it).contains("intellij.sample.tests.xml").contains("Generate Product Layouts") },
    )
    assertThat(tempDir.resolve("intellij.sample.tests/testResources/intellij.sample.tests.xml")).doesNotExist()
  }

  @Test
  fun `a missing descriptor is created in the test resource root when writes are allowed`() {
    testModule("intellij.sample.tests", descriptor = false)
    val runner = runner(additionalModules = listOf("$TEST_MODULE_NAME_PLACEHOLDER._test_plugin"), enabledForModules = listOf("intellij.sample.tests"))

    val result = computeSyntheticTestPlugins(listOf(runner), bundledPluginsByProduct = mapOf("Sample" to listOf("intellij.sample.plugin")), outputProvider = createTestModuleOutputProvider(project), createMissingDescriptors = true)

    assertThat(result.problems).isEmpty()
    assertThat(result.specsByProduct.getValue("Sample").map { it.pluginId.value }).containsExactly("intellij.sample.tests._test_plugin")
    assertThat(tempDir.resolve("intellij.sample.tests/testResources/intellij.sample.tests.xml").readText()).isEqualTo("<idea-plugin>\n</idea-plugin>\n")
  }

  @Test
  fun `a module without a test resource root is reported`() {
    testModule("intellij.sample.tests", descriptor = false, testResourceRoot = false)
    val runner = runner(additionalModules = listOf("$TEST_MODULE_NAME_PLACEHOLDER._test_plugin"), enabledForModules = listOf("intellij.sample.tests"))

    val result = computeSyntheticTestPlugins(listOf(runner), bundledPluginsByProduct = mapOf("Sample" to listOf("intellij.sample.plugin")), outputProvider = createTestModuleOutputProvider(project), createMissingDescriptors = true)

    assertThat(result.specsByProduct).isEmpty()
    assertThat(result.problems.single().message).contains("no test resource root").contains("<idea-plugin>")
  }

  private fun runner(
    additionalModules: List<String>,
    enabledForModules: List<String>,
    entryPointModule: String = TEST_MODULE_NAME_PLACEHOLDER,
    disabledForModules: List<String> = emptyList(),
    platformPrefix: String = "Sample",
  ): DevBuildServerSettings {
    return DevBuildServerSettings(
      name = "Sample JUnit (dev mode)",
      enabledForModules = enabledForModules,
      disabledForModules = disabledForModules,
      jvmArgs = listOf("-Didea.platform.prefix=$platformPrefix", "-Dadditional.modules=${additionalModules.joinToString(",")}", "-Didea.dev.build.test.entry.point.module=$entryPointModule"),
    )
  }

  private fun testModule(name: String, descriptor: Boolean = true, testResourceRoot: Boolean = true): JpsModule {
    val module = project.addModule(name, JpsJavaModuleType.INSTANCE)
    val dir = Files.createDirectories(tempDir.resolve(name))
    module.addSourceRoot("file://${Files.createDirectories(dir.resolve("testSrc")).invariantSeparatorsPathString}", JavaSourceRootType.TEST_SOURCE)
    if (testResourceRoot) {
      val resources = Files.createDirectories(dir.resolve("testResources"))
      module.addSourceRoot("file://${resources.invariantSeparatorsPathString}", JavaResourceRootType.TEST_RESOURCE)
      if (descriptor) {
        Files.writeString(resources.resolve("$name.xml"), "<idea-plugin/>")
      }
    }
    return module
  }
}
