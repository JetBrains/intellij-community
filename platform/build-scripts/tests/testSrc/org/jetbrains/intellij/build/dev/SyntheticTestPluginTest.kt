// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.ModuleOutputProvider
import org.jetbrains.intellij.build.impl.JpsModuleOutputProviderState
import org.jetbrains.intellij.build.impl.PRODUCT_DESCRIPTOR_META_PATH
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.JpsProject
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JpsJavaDependencyScope
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

class SyntheticTestPluginTest {
  @TempDir
  lateinit var tempDir: Path

  private val project: JpsProject = JpsElementFactory.getInstance().createModel().project

  @Test
  fun `the content is the test module, the base, and the modules the descriptors reach that the product lacks`() {
    val tests = testModule("intellij.sample.tests", descriptor = descriptor(dependencies = listOf("intellij.sample", "intellij.libraries.junit5")))
    val production = productionModule("intellij.sample", descriptor = descriptor(dependencies = listOf("intellij.libraries.jna")))
    productionModule("intellij.libraries.jna", descriptor = descriptor())
    baseModules(testFrameworkDependencies = listOf("intellij.libraries.junit5", "intellij.java.rt"))
    productionModule("intellij.libraries.junit5", descriptor = descriptor(dependencies = listOf("intellij.libraries.opentest4j")))
    productionModule("intellij.libraries.opentest4j", descriptor = descriptor())
    productionModule("intellij.java.rt", descriptor = descriptor())
    // a JPS dependency no descriptor declares is not content: the loader resolves nothing through it
    tests.dependsOn(productionModule("intellij.sample.helper", descriptor = descriptor()), JpsJavaDependencyScope.TEST)

    val content = computeSyntheticTestPluginContent(targetModuleName = tests.name, outputProvider = outputProvider(), provided = setOf(production.name, "intellij.tools.testsBootstrap"))

    assertThat(content).containsExactly("intellij.sample.tests", "intellij.libraries.junit5", "intellij.libraries.opentest4j", "intellij.platform.testFramework", "intellij.java.rt")
  }

  @Test
  fun `a test module, a test support module and a library the test module reaches are content`() {
    baseModules()
    val tests = testModule("intellij.sample.tests", descriptor = descriptor(dependencies = listOf("intellij.sample.tests.common", "intellij.sample.testFramework", "intellij.libraries.assertj")))
    testModule("intellij.sample.tests.common", descriptor = descriptor())
    productionModule("intellij.sample.testFramework", descriptor = descriptor())
    productionModule("intellij.libraries.assertj", descriptor = descriptor())

    val content = computeSyntheticTestPluginContent(targetModuleName = tests.name, outputProvider = outputProvider(), provided = emptySet())

    assertThat(content).contains("intellij.sample.tests.common", "intellij.sample.testFramework", "intellij.libraries.assertj")
  }

  @Test
  fun `a production module the product does not provide is an error that names each such module and what reached it`() {
    baseModules()
    val tests = testModule("intellij.sample.tests", descriptor = descriptor(dependencies = listOf("intellij.sample.testFramework", "intellij.sample.other")))
    productionModule("intellij.sample.testFramework", descriptor = descriptor(dependencies = listOf("intellij.sample.service")))
    productionModule("intellij.sample.service", descriptor = descriptor())
    productionModule("intellij.sample.other", descriptor = descriptor())

    assertThatThrownBy { computeSyntheticTestPluginContent(targetModuleName = tests.name, outputProvider = outputProvider(), provided = emptySet()) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'intellij.sample.service' (reached from intellij.sample.testFramework)")
      .hasMessageContaining("'intellij.sample.other' (reached from intellij.sample.tests)")
      .hasMessageContaining("production roots")
  }

  @Test
  fun `a module without a descriptor is an error that names the file to create`() {
    baseModules()
    val tests = testModule("intellij.sample.tests", descriptor = descriptor(dependencies = listOf("intellij.sample.util")))
    productionModule("intellij.sample.util", descriptor = null)

    assertThatThrownBy { computeSyntheticTestPluginContent(targetModuleName = tests.name, outputProvider = outputProvider(), provided = emptySet()) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("'intellij.sample.util' (reached from intellij.sample.tests) has no descriptor")
      .hasMessageContaining("intellij.sample.util.xml")
  }

  @Test
  fun `the product provides what the core and the bundled plugins register in the jetbrains namespace, and not their own copies`() {
    val platformLayout = PlatformLayout()
    platformLayout.withModule("intellij.java.rt")
    platformLayout.withModule("intellij.platform.lang")
    platformLayout.descriptorCacheContainer.forPlatform(platformLayout).put(
      PRODUCT_DESCRIPTOR_META_PATH,
      "<idea-plugin><content namespace=\"jetbrains\"><module name=\"intellij.platform.lang\"/></content><content><module name=\"intellij.libraries.core.private\"/></content></idea-plugin>".toByteArray(),
    )
    val bundled = pluginModule(
      "intellij.other.plugin",
      pluginXml = "<idea-plugin><id>other</id><content namespace=\"jetbrains\"><module name=\"intellij.other.impl\"/></content><content><module name=\"intellij.libraries.opentest4j\"/></content></idea-plugin>",
    )
    val bundledLayout = PluginLayout.plugin(bundled.name)
    bundledLayout.withModule("intellij.other.packed")

    val provided = providedModules(platformLayout = platformLayout, bundledPlugins = listOf(bundledLayout), outputProvider = outputProvider())

    assertThat(provided).containsExactlyInAnyOrder("intellij.platform.lang", "intellij.other.plugin", "intellij.other.impl")
  }

  @Test
  fun `the descriptor declares the content in the jetbrains namespace`() {
    val xml = renderSyntheticTestPluginXml(
      pluginModuleName = "intellij.sample.tests._test_plugin",
      targetModuleName = "intellij.sample.tests",
      content = listOf("intellij.sample.tests", "intellij.platform.testFramework"),
    )

    assertThat(xml).isEqualTo("""
      <idea-plugin>
        <id>intellij.sample.tests._test_plugin</id>
        <name>Test plugin for intellij.sample.tests (made by the dev build)</name>
        <vendor>JetBrains</vendor>

        <content namespace="jetbrains">
          <module name="intellij.sample.tests"/>
          <module name="intellij.platform.testFramework"/>
        </content>
      </idea-plugin>

    """.trimIndent())
  }

  private fun outputProvider(): ModuleOutputProvider = JpsModuleOutputProviderState(project).createProvider(useTestCompilationOutput = true)

  private fun descriptor(dependencies: List<String> = emptyList()): String {
    return "<idea-plugin><dependencies>${dependencies.joinToString("") { "<module name=\"$it\"/>" }}</dependencies></idea-plugin>"
  }

  private fun baseModules(testFrameworkDependencies: List<String> = emptyList()) {
    for (name in BASE_MODULES) {
      productionModule(name, descriptor = descriptor(dependencies = if (name == "intellij.platform.testFramework") testFrameworkDependencies else emptyList()))
    }
  }

  private fun testModule(name: String, descriptor: String?): JpsModule {
    val module = project.addModule(name, JpsJavaModuleType.INSTANCE)
    val dir = Files.createDirectories(tempDir.resolve(name))
    module.addSourceRoot("file://${Files.createDirectories(dir.resolve("testSrc")).invariantSeparatorsPathString}", JavaSourceRootType.TEST_SOURCE)
    val resources = Files.createDirectories(dir.resolve("testResources"))
    module.addSourceRoot("file://${resources.invariantSeparatorsPathString}", JavaResourceRootType.TEST_RESOURCE)
    if (descriptor != null) {
      Files.writeString(resources.resolve("$name.xml"), descriptor)
    }
    return module
  }

  private fun pluginModule(name: String, pluginXml: String): JpsModule {
    val module = productionModule(name, descriptor = null)
    val metaInf = Files.createDirectories(tempDir.resolve(name).resolve("resources/META-INF"))
    Files.writeString(metaInf.resolve("plugin.xml"), pluginXml)
    return module
  }

  private fun productionModule(name: String, descriptor: String?): JpsModule {
    val module = project.addModule(name, JpsJavaModuleType.INSTANCE)
    val dir = Files.createDirectories(tempDir.resolve(name))
    module.addSourceRoot("file://${Files.createDirectories(dir.resolve("src")).invariantSeparatorsPathString}", JavaSourceRootType.SOURCE)
    val resources = Files.createDirectories(dir.resolve("resources"))
    module.addSourceRoot("file://${resources.invariantSeparatorsPathString}", JavaResourceRootType.RESOURCE)
    if (descriptor != null) {
      Files.writeString(resources.resolve("$name.xml"), descriptor)
    }
    return module
  }

  private fun JpsModule.dependsOn(other: JpsModule, scope: JpsJavaDependencyScope) {
    val dependency = dependenciesList.addModuleDependency(other)
    JpsJavaExtensionService.getInstance().getOrCreateDependencyExtension(dependency).scope = scope
  }
}
