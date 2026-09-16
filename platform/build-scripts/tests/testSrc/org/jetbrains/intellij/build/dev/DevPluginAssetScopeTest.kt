// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.devDist.DISTRIBUTION_ASSET_SCOPE
import org.jetbrains.intellij.build.devDist.PLUGIN_ASSET_SCOPE
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingProjection
import org.jetbrains.intellij.build.devDist.planPluginPacking
import org.jetbrains.intellij.build.devDist.pluginPackingExecutionVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class DevPluginAssetScopeTest {
  @Test
  fun `distribution asset requires execution version three`() {
    val asset = PluginPackingAsset(
      destination = "lib/native.bin",
      inputs = listOf("input"),
      classPath = false,
      scope = DISTRIBUTION_ASSET_SCOPE,
    )

    assertThat(pluginPackingExecutionVersion(listOf(asset))).isEqualTo(3)
  }

  @Test
  fun `plugin and distribution assets keep separate roots`(@TempDir tempDir: Path) {
    val pluginInput = Files.writeString(tempDir.resolve("plugin-input"), "plugin")
    val distributionInput = Files.writeString(tempDir.resolve("distribution-input"), "distribution")
    val assets = listOf(
      PluginPackingAsset(destination = "lib/native.bin", inputs = listOf("plugin-input")),
      PluginPackingAsset(
        destination = "lib/native.bin",
        inputs = listOf("distribution-input"),
        classPath = false,
        scope = DISTRIBUTION_ASSET_SCOPE,
      ),
    )
    val plan = planPluginPacking(
      plugin = "test.plugin",
      variant = "linux_x64",
      assets = assets,
      preparations = emptyList(),
      preparationRoots = emptyList(),
      artifacts = emptyList(),
    )
    val projection = PluginPackingProjection(
      version = 3,
      plugin = plan.plugin,
      variant = plan.variant,
      layoutSignature = plan.layoutSignature,
      assets = assets,
    )
    assertThat(projection.plan().assets.map { it.asset.scope })
      .containsExactly(PLUGIN_ASSET_SCOPE, DISTRIBUTION_ASSET_SCOPE)

    val catalogue = DevPluginArtifactCatalogue(
      artifacts = listOf(
        DevPluginArtifact(id = "plugin-input", kind = "file", root = pluginInput.toString()),
        DevPluginArtifact(id = "distribution-input", kind = "file", root = distributionInput.toString()),
      )
    )
    val derivation = deriveDevPluginInputs(plan, catalogue.toPlanCatalogue(), emptyList())
    assertThat(derivation.inputs).containsExactly("plugin-input", "distribution-input")

    val result = prepareDevPlugin(
      plan = plan,
      runtimeLayoutSignature = plan.layoutSignature,
      remainderInputIds = derivation.inputs,
      catalogue = catalogue,
      cachedDescriptorContent = "<idea-plugin/>".toByteArray(),
      pluginDirectory = Path.of("plugins/test"),
      outputDirectory = tempDir.resolve("prepared-output"),
    )
    assertThat(result.recipe.version).isEqualTo(3)
    assertThat(result.recipe.assets.map { it.scope })
      .containsExactly(PLUGIN_ASSET_SCOPE, DISTRIBUTION_ASSET_SCOPE)
    assertThat(result.recipe.operations.map { it.scope })
      .containsExactly(PLUGIN_ASSET_SCOPE, DISTRIBUTION_ASSET_SCOPE)
  }

  @Test
  fun `distribution assets require a safe non-classpath scope`() {
    assertThatThrownBy {
      planPluginPacking(
        plugin = "test.plugin",
        variant = "linux_x64",
        assets = listOf(PluginPackingAsset(destination = "lib/native.bin", inputs = listOf("input"), scope = "unknown")),
        preparations = emptyList(),
        preparationRoots = emptyList(),
        artifacts = emptyList(),
      )
    }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("scope")

    assertThatThrownBy {
      planPluginPacking(
        plugin = "test.plugin",
        variant = "linux_x64",
        assets = listOf(
          PluginPackingAsset(
            destination = "lib/native.bin",
            inputs = listOf("input"),
            scope = DISTRIBUTION_ASSET_SCOPE,
          )
        ),
        preparations = emptyList(),
        preparationRoots = emptyList(),
        artifacts = emptyList(),
      )
    }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("classpath")
  }

  @Test
  fun `distribution tree cannot own the distribution root`() {
    assertThatThrownBy {
      planPluginPacking(
        plugin = "test.plugin",
        variant = "linux_x64",
        assets = listOf(
          PluginPackingAsset(
            destination = "",
            inputs = listOf("input"),
            kind = "tree",
            classPath = false,
            scope = DISTRIBUTION_ASSET_SCOPE,
          )
        ),
        preparations = emptyList(),
        preparationRoots = emptyList(),
        artifacts = emptyList(),
      )
    }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("destination")
  }

  @Test
  fun `resource consumer keeps duplicate paths in separate scopes`(@TempDir tempDir: Path) {
    val resourceFile = Files.writeString(tempDir.resolve("resource.bin"), "resource")
    val distributionFile = Files.writeString(tempDir.resolve("distribution.bin"), "distribution")
    val catalogue = DevPluginArtifactCatalogue(artifacts = listOf(
      DevPluginArtifact(id = "resource-input", kind = "file", root = resourceFile.toString()),
      DevPluginArtifact(id = "distribution-input", kind = "file", root = distributionFile.toString()),
    ))
    val resourceInput = DevPluginReference("resource-input")
    val source = captureDevPluginResourceSource(
      catalogue = catalogue,
      moduleName = "resource.module",
      resourcePath = "resource.bin",
      input = resourceInput,
    )
    val core = DevPluginResourcePreparationCore(
      mainModule = "test.plugin",
      resources = listOf(DevPluginResourceSpec(
        moduleName = "resource.module",
        resourcePath = "resource.bin",
        relativeOutputPath = "lib",
        packToZip = false,
      )),
      catalogue = catalogue,
      sources = listOf(source),
    )
    val effect = core.requireEffects().values.single()
    val resourceAsset = effect.assets.single()
    val distributionAsset = resourceAsset.copy(
      inputs = listOf("distribution-input"),
      scope = DISTRIBUTION_ASSET_SCOPE,
    )
    val plan = planPluginPacking(
      plugin = "test.plugin",
      variant = "linux_x64",
      assets = listOf(resourceAsset, distributionAsset),
      preparations = listOf(effect.preparation),
      preparationRoots = emptyList(),
      artifacts = emptyList(),
    )

    assertThat(core.compileActions(plan, catalogue)).containsOnlyKeys(effect.preparation.id)
  }
}
