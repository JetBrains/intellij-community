// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.jetbrains.intellij.build.dev.DevPluginLayoutAsset
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetOwner
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSource
import org.jetbrains.intellij.build.dev.DevPluginLayoutAssetSpec
import org.jetbrains.intellij.build.impl.BazelTargetsInfo
import org.jetbrains.jps.model.JpsElementFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The binding of [DevPluginLayoutAssetSource.OptionalLocalDirectory]: a local backend part below `out/bundle-plugins`
 * becomes one optional source tree of the root filegroup. The Bazel rule materializes an absent tree as empty.
 */
class DevDistOptionalLocalDirectoryTest {
  @Test
  fun `a backend part below out bundle-plugins binds the root filegroup as an optional tree`(@TempDir checkout: Path) {
    val bindings = bind(checkout, "out/bundle-plugins/backend-a")

    val input = bindings.catalogueFacts.additionalInputs.single()
    assertThat(input).isEqualTo(DevDistPluginRawInput(
      id = "optional-local-directory:out/bundle-plugins/backend-a",
      label = "//:dev_dist_optional_local_directories",
      kind = "directory",
      fileName = "backend-a",
      sourceTreePrefix = "out/bundle-plugins/backend-a",
      optionalSourceTree = true,
    ))
    assertThat(bindings.catalogueFacts.fileFacts).containsExactly(entry(input.id, DevDistPluginFileFacts("directory", "backend-a")))
    assertThat(bindings.catalogueFacts.additionalLibraries).isEmpty()
    assertThat(bindings.facts.omittedSlots).isEmpty()
    val operation = bindings.operations.single()
    assertThat(operation.inputs.single().artifact).isEqualTo(input.id)
    val layoutAssets = operation.layoutAssets!!
    assertThat(layoutAssets.format).isEqualTo("tree")
    assertThat(layoutAssets.root).isEmpty()
    assertThat(layoutAssets.assets.single().destination).isEmpty()
  }

  @Test
  fun `a backend part of a per-product out directory is accepted`(@TempDir checkout: Path) {
    for (path in listOf("out/product/bundle-plugins/unity", "out/product/bundle-plugins/unity/backend", "out/bundle-plugins/godot/sdk")) {
      val input = bind(checkout, path).catalogueFacts.additionalInputs.single()

      assertThat(input.id).isEqualTo("optional-local-directory:$path")
      assertThat(input.sourceTreePrefix).isEqualTo(path)
      assertThat(input.fileName).isEqualTo(path.substringAfterLast('/'))
      assertThat(input.optionalSourceTree).isTrue()
    }
  }

  @Test
  fun `a directory outside out bundle-plugins is rejected`(@TempDir checkout: Path) {
    for (path in listOf("", "lib/bundle-plugins/x", "out/x/y/bundle-plugins/z", "out/bundle-plugin/x", "bundle-plugins/x", "out/product/bundle-plugins")) {
      assertThatThrownBy { bind(checkout, path) }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessage("Layout callback '$KEY' names unsupported optional local directory '$path'")
    }
  }

  @Test
  fun `a path with an empty or a relative segment is rejected`(@TempDir checkout: Path) {
    for (path in listOf("out/bundle-plugins/", "out/bundle-plugins/../lib", "out/bundle-plugins/./x", "out/bundle-plugins//x")) {
      assertThatThrownBy { bind(checkout, path) }
        .isInstanceOf(IllegalArgumentException::class.java)
        .hasMessage("Layout callback '$KEY' names unsafe optional local directory '$path'")
    }
  }

  @Test
  fun `two callbacks of one directory share the input and two directories have their own`(@TempDir checkout: Path) {
    val spec = DevPluginLayoutAssetSpec(
      sources = listOf(
        DevPluginLayoutAssetSource.OptionalLocalDirectory("out/bundle-plugins/backend-a"),
        DevPluginLayoutAssetSource.OptionalLocalDirectory("out/bundle-plugins/backend-a"),
        DevPluginLayoutAssetSource.OptionalLocalDirectory("out/bundle-plugins/backend-b"),
      ),
      assets = listOf(
        DevPluginLayoutAsset(destination = "backend/fsharp", sources = listOf(0)),
        DevPluginLayoutAsset(destination = "backend/fsharp-copy", sources = listOf(1)),
        DevPluginLayoutAsset(destination = "backend/unity", sources = listOf(2)),
      ),
    )

    val bindings = bind(checkout, spec)

    assertThat(bindings.catalogueFacts.additionalInputs.map { it.id }).containsExactly(
      "optional-local-directory:out/bundle-plugins/backend-a",
      "optional-local-directory:out/bundle-plugins/backend-b",
    )
    assertThat(bindings.catalogueFacts.additionalInputs).allSatisfy { assertThat(it.optionalSourceTree).isTrue() }
    val operation = bindings.operations.single()
    assertThat(operation.layoutAssets!!.root).isEqualTo("backend")
    assertThat(operation.layoutAssets!!.assets.map { it.destination }).containsExactly("fsharp", "fsharp-copy", "unity")
  }

  @Test
  fun `a tree at the plugin root cannot share the callback with another destination`(@TempDir checkout: Path) {
    val spec = DevPluginLayoutAssetSpec(
      sources = listOf(
        DevPluginLayoutAssetSource.OptionalLocalDirectory("out/bundle-plugins/backend-a"),
        DevPluginLayoutAssetSource.OptionalLocalDirectory("out/bundle-plugins/backend-b"),
      ),
      assets = listOf(
        DevPluginLayoutAsset(destination = "", sources = listOf(0)),
        DevPluginLayoutAsset(destination = "backend", sources = listOf(1)),
      ),
    )

    assertThatThrownBy { bind(checkout, spec) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageStartingWith("Prepared layout assets cannot mix the plugin root with another destination")
  }

  private fun bind(checkout: Path, path: String): GeneratedDevPluginLayoutAssetBindings {
    return bind(checkout, DevPluginLayoutAssetSpec(
      sources = listOf(DevPluginLayoutAssetSource.OptionalLocalDirectory(path)),
      assets = listOf(DevPluginLayoutAsset(destination = "", sources = listOf(0))),
    ))
  }

  private fun bind(checkout: Path, spec: DevPluginLayoutAssetSpec): GeneratedDevPluginLayoutAssetBindings {
    val owner = object : DevPluginLayoutAssetOwner {
      override val devPluginLayoutAssetSpec: DevPluginLayoutAssetSpec = spec
    }
    return generateDevPluginLayoutAssetBindings(
      key = KEY,
      owner = owner,
      requestedFormat = "tree",
      index = DevDistBazelIndex(
        targets = BazelTargetsInfo.TargetsFile(modules = emptyMap(), projectLibraries = emptyMap(), pluginDistributionTargets = emptyMap()),
        projectRoot = checkout,
      ),
      binder = CommunityDevDistHalf.assetBinder,
      outputProvider = SourceRootModuleOutputProvider(JpsElementFactory.getInstance().createModel().project),
    )
  }
}

private const val KEY = "resource-generator:0"
