// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.dev.DevPluginLayoutAsset
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetOwner
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSource
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import org.jetbrains.intellij.build.dev.DevPluginResourceExclusions
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.java.JpsJavaModuleType
import org.jetbrains.jps.util.JpsPathUtil
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class DevDistResourceResolverTest {
  @Test
  fun `a directory in a module-less nested package uses its hand-written filegroup`(@TempDir checkout: Path) {
    val moduleRoot = prepareModulePackage(checkout, declaresFilegroup = true)
    Files.writeString(Files.createDirectories(moduleRoot.resolve("lib/sampleData")).resolve("sample.txt"), "sample\n")

    val input = bindings(checkout, "lib/sampleData").catalogueFacts.additionalInputs.single()

    assertThat(input).isEqualTo(
      DevDistPluginRawInput(
        id = "module-resource:resource-generator:0:0:source",
        label = "//plugins/x/lib:dev_dist_resources",
        kind = "directory",
        fileName = "sampleData",
        sourceTreePrefix = "plugins/x/lib/sampleData",
      ),
    )
  }

  @Test
  fun `a directory in a module-less nested package needs a hand-written filegroup`(@TempDir checkout: Path) {
    val moduleRoot = prepareModulePackage(checkout, declaresFilegroup = false)
    Files.writeString(Files.createDirectories(moduleRoot.resolve("lib/sampleData")).resolve("sample.txt"), "sample\n")

    val error = assertThrows<DevDistUnplannableLayoutException> { bindings(checkout, "lib/sampleData") }

    assertThat(error).hasMessageContaining("is a Bazel package without a module, and its BUILD file declares no 'dev_dist_resources' filegroup by hand")
  }

  @Test
  fun `a filtered directory names the filtered filegroup of its package`(@TempDir checkout: Path) {
    val moduleRoot = prepareModulePackage(checkout, declaresFilegroup = false)
    Files.writeString(Files.createDirectories(moduleRoot.resolve("helpers/nested")).resolve("helper.py"), "helper\n")
    val exclusions = DevPluginResourceExclusions(files = listOf("setup.py"), directories = listOf("tests"))

    val input = bindings(checkout, "helpers", exclusions).catalogueFacts.additionalInputs.single()

    assertThat(input).isEqualTo(
      DevDistPluginRawInput(
        id = "module-resource:resource-generator:0:0:source",
        label = "//plugins/x:dev_dist_resources_helpers",
        kind = "directory",
        fileName = "helpers",
        sourceTreePrefix = "plugins/x/helpers",
        sourceTreeExclusions = exclusions,
      ),
    )
  }

  @Test
  fun `a filtered directory in a module-less nested package has no generated owner`(@TempDir checkout: Path) {
    val moduleRoot = prepareModulePackage(checkout, declaresFilegroup = true)
    Files.writeString(Files.createDirectories(moduleRoot.resolve("lib/sampleData")).resolve("sample.txt"), "sample\n")

    val error = assertThrows<DevDistUnplannableLayoutException> {
      bindings(checkout, "lib/sampleData", DevPluginResourceExclusions(files = listOf("drop.txt")))
    }

    assertThat(error).hasMessageContaining("has no module, so no dev section declares the filtered filegroup")
  }

  @Test
  fun `a file in a module-less nested package has no generated owner`(@TempDir checkout: Path) {
    val moduleRoot = prepareModulePackage(checkout, declaresFilegroup = true)
    Files.writeString(moduleRoot.resolve("lib/sampleData.txt"), "sample\n")

    val error = assertThrows<DevDistUnplannableLayoutException> { bindings(checkout, "lib/sampleData.txt") }

    assertThat(error).hasMessageContaining("is a Bazel package without a module, so no dev section exports the file")
  }

  private fun prepareModulePackage(checkout: Path, declaresFilegroup: Boolean): Path {
    val moduleRoot = Files.createDirectories(checkout.resolve("plugins/x"))
    Files.writeString(moduleRoot.resolve("BUILD.bazel"), "")
    val nestedPackage = Files.createDirectories(moduleRoot.resolve("lib"))
    Files.writeString(
      nestedPackage.resolve("BUILD.bazel"),
      if (declaresFilegroup) "filegroup(name = \"dev_dist_resources\")\n" else "",
    )
    return moduleRoot
  }

  private fun bindings(
    checkout: Path,
    resourcePath: String,
    exclusions: DevPluginResourceExclusions = DevPluginResourceExclusions.NONE,
  ) = generateDevPluginLayoutAssetBindings(
    key = "resource-generator:0",
    owner = object : DevPluginLayoutAssetOwner {
      override val devPluginLayoutAssetSpec = DevPluginLayoutAssetSpec(
        sources = listOf(DevPluginLayoutAssetSource.ModuleDirectory("intellij.x", resourcePath, exclusions)),
        assets = listOf(DevPluginLayoutAsset(destination = "resources", sources = listOf(0))),
      )
    },
    requestedFormat = "tree",
    index = syntheticIndex(checkout, "intellij.x" to "//plugins/x:x.jar"),
    binder = CommunityDevDistHalf.assetBinder,
    outputProvider = sourceRootOutputProvider(checkout),
  )

  private fun sourceRootOutputProvider(checkout: Path): SourceRootModuleOutputProvider {
    val project = JpsElementFactory.getInstance().createModel().project
    project.addModule("intellij.x", JpsJavaModuleType.INSTANCE).contentRootsList.addUrl(
      JpsPathUtil.pathToUrl(checkout.resolve("plugins/x").toString()),
    )
    return SourceRootModuleOutputProvider(project)
  }
}