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
  fun `plugin and distribution assets keep separate roots`() {
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
    assertThat(plan.requiredInputs).containsExactly("plugin-input", "distribution-input")
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
}
