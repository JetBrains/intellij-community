// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.ProductProperties
import org.jetbrains.intellij.build.dev.DevPluginResourceExclusions
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * A `withResourceTree` declaration binds as a plain tree copy, as a `withResource*` directory does. With exclusions it
 * reads the filtered filegroup of its package. No operation and no preparation stand behind it, so the copy alone keeps
 * a plugin simple. A second tree over one destination is refused.
 */
class DevDistResourceTreeBindingTest {
  @TempDir
  lateinit var checkout: Path

  private val plugin = "intellij.x"

  private fun bindings(layout: PluginLayout): GeneratedDevPluginBindings {
    val packageDir = Files.createDirectories(checkout.resolve("plugins/x"))
    Files.writeString(packageDir.resolve("BUILD.bazel"), "")
    Files.writeString(Files.createDirectories(packageDir.resolve("helpers/tests")).resolve("test_a.py"), "")
    Files.writeString(packageDir.resolve("helpers/a.py"), "")
    val project = JpsElementFactory.getInstance().createModel().project
    project.addModule(plugin, JpsJavaModuleType.INSTANCE).contentRootsList.addUrl(JpsPathUtil.pathToUrl(packageDir.toString()))
    val request = DevDistPluginRequest(
      product = "idea",
      properties = SyntheticProperties(),
      tier = DevDistPluginTier.BUNDLED,
      variant = PluginSymbolicVariant(id = "common"),
      layout = layout,
    )
    val index = syntheticIndex(checkout, plugin to "//plugins/x:x.jar")
    return generateDevPluginAssetBindings(request, index, SourceRootModuleOutputProvider(project), RefusingDevDistAssetBinder("test"))
  }

  @Test
  fun `a resource tree with exclusions is a plain copy of the filtered filegroup`() {
    val layout = PluginLayout.pluginAutoWithCustomDirName(plugin) {
      it.withResourceTree(moduleName = plugin, resourcePath = "helpers", relativeOutputPath = "helpers", excludedDirectories = listOf("tests"))
    }

    val bindings = bindings(layout)

    val inputId = "module-resource:resource-generator:0:0:source"
    assertThat(bindings.operations).isEmpty()
    assertThat(bindings.facts.effects).isEmpty()
    assertThat(bindings.facts.declaredAssets).containsExactly(entry(
      "resource-generator:0",
      listOf(PluginPackingAsset(destination = "helpers", inputs = listOf(inputId), kind = "tree", classPath = false)),
    ))
    assertThat(bindings.catalogueFacts.additionalInputs).containsExactly(DevDistPluginRawInput(
      id = inputId,
      label = "//plugins/x:dev_dist_resources_helpers",
      kind = "directory",
      fileName = "helpers",
      sourceTreePrefix = "plugins/x/helpers",
      sourceTreeExclusions = DevPluginResourceExclusions(directories = listOf("tests")),
    ))
  }

  @Test
  fun `a resource tree without exclusions reads the package filegroup`() {
    val layout = PluginLayout.pluginAutoWithCustomDirName(plugin) {
      it.withResourceTree(moduleName = plugin, resourcePath = "helpers", relativeOutputPath = "lib/helpers")
    }

    val bindings = bindings(layout)

    val input = bindings.catalogueFacts.additionalInputs.single()
    assertThat(input.label).isEqualTo("//plugins/x:dev_dist_resources")
    assertThat(input.sourceTreeExclusions).isEqualTo(DevPluginResourceExclusions.NONE)
    assertThat(bindings.facts.declaredAssets.getValue("resource-generator:0").single().destination).isEqualTo("lib/helpers")
    assertThat(bindings.operations).isEmpty()
  }

  @Test
  fun `a resource tree over the destination of a withResource directory is refused`() {
    val layout = PluginLayout.pluginAutoWithCustomDirName(plugin) {
      it.withResource("helpers", "helpers")
      it.withResourceTree(moduleName = plugin, resourcePath = "helpers", relativeOutputPath = "helpers", excludedDirectories = listOf("tests"))
    }

    assertThatThrownBy { bindings(layout) }
      .isInstanceOf(DevDistUnplannableLayoutException::class.java)
      .hasMessageContaining("Plugin '$plugin'")
      .hasMessageContaining("destination 'helpers'")
      .hasMessageContaining("one by one")
  }
}

private class SyntheticProperties : ProductProperties() {
  override val baseFileName: String = "synthetic"
  override fun getBaseArtifactName(appInfo: ApplicationInfoProperties, buildNumber: String): String = "synthetic"
  override fun createWindowsCustomizer(projectHome: Path) = null
  override fun createLinuxCustomizer(projectHome: Path) = null
  override fun createMacCustomizer(projectHome: Path) = null
  override fun getProductContentDescriptor() = null
}
