// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The entry of the bridge descriptor index, as the generator computes it. The fixture mirrors `_module_descriptor_index_test`
 * of `jps_library_derivation_pipeline_test.bzl`, so the two rules cannot drift apart unnoticed.
 */
class DevDistBridgeDescriptorLabelTest {
  @TempDir
  lateinit var dir: Path

  private val moduleName = "intellij.test.content"

  /**
   * A module with four production resource roots in `.iml` order: one the jar maps into `META-INF`, one outside the
   * package, and two plain ones. A test resource root follows. Each root holds `<module name>.xml`.
   */
  private fun module(): JpsModule {
    val packageDirectory = dir.resolve("community/plugins/test/content")
    val module = JpsElementFactory.getInstance().createModel().project.addModule(moduleName, JpsJavaModuleType.INSTANCE)
    module.contentRootsList.addUrl(JpsPathUtil.pathToUrl(packageDirectory.toString()))
    val java = JpsJavaExtensionService.getInstance()
    module.addSourceRoot(url(packageDirectory.resolve("remapped")), JavaResourceRootType.RESOURCE, java.createResourceRootProperties("META-INF", false))
    module.addSourceRoot(url(packageDirectory.resolve("../outside")), JavaResourceRootType.RESOURCE)
    module.addSourceRoot(url(packageDirectory.resolve("first")), JavaResourceRootType.RESOURCE)
    module.addSourceRoot(url(packageDirectory.resolve("second")), JavaResourceRootType.RESOURCE)
    module.addSourceRoot(url(packageDirectory.resolve("tests")), JavaResourceRootType.TEST_RESOURCE)
    for (directory in listOf("content/remapped", "outside", "content/first", "content/second", "content/tests")) {
      val file = dir.resolve("community/plugins/test/$directory/$moduleName.xml")
      Files.createDirectories(file.parent)
      Files.writeString(file, "<idea-plugin/>")
    }
    return module
  }

  private fun url(path: Path): String = JpsPathUtil.pathToUrl(path.toString())

  private fun descriptorFile(directory: String): Path = dir.resolve("community/plugins/test/$directory/$moduleName.xml")

  @Test
  fun `the first plain resource root inside the package gives the entry`() {
    val module = module()
    val index = syntheticIndex(dir, moduleName to "@community//plugins/test/content:content.jar")

    assertThat(bridgeDescriptorLabel(module, index)).isEqualTo("@community//plugins/test/content:first/$moduleName.xml")

    // The second root gives the entry when the first one holds no descriptor.
    Files.delete(descriptorFile("content/first"))
    assertThat(bridgeDescriptorLabel(module, index)).isEqualTo("@community//plugins/test/content:second/$moduleName.xml")

    // The remapped root and the root outside the package give no entry.
    Files.delete(descriptorFile("content/second"))
    assertThat(bridgeDescriptorLabel(module, index)).isNull()
  }

  @Test
  fun `a file on the other repository half gives no entry`() {
    val module = module()
    // An ultimate module in the root package: every descriptor lies below the community checkout.
    val index = syntheticIndex(dir, moduleName to "//:content.jar")

    assertThat(bridgeDescriptorLabel(module, index)).isNull()
  }

  @Test
  fun `a module the targets JSON does not place gives no entry`() {
    assertThat(bridgeDescriptorLabel(module(), syntheticIndex(dir))).isNull()
  }

  @Test
  fun `the leaf derives only the row of a content module at its bridge entry`() {
    val label = "@community//plugins/test/content:first/$moduleName.xml"
    val bridgeLabel: (String) -> String? = { if (it == moduleName) label else null }

    assertThat(isBridgeDerivedDescriptor(label, "$moduleName.xml", listOf(moduleName), bridgeLabel)).isTrue()
    // Another label, another load path, or a module outside the content modules keeps the row explicit.
    assertThat(isBridgeDerivedDescriptor("@community//plugins/test/content:second/$moduleName.xml", "$moduleName.xml", listOf(moduleName), bridgeLabel)).isFalse()
    assertThat(isBridgeDerivedDescriptor(label, "META-INF/$moduleName.xml", listOf(moduleName), bridgeLabel)).isFalse()
    assertThat(isBridgeDerivedDescriptor(label, "$moduleName.xml", listOf("intellij.test.other"), bridgeLabel)).isFalse()
    assertThat(isBridgeDerivedDescriptor(label, moduleName, listOf(moduleName), bridgeLabel)).isFalse()
  }
}
