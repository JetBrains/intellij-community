// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dev

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.devDist.NATIVE_TREE_INPUT_PREFIX
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingProjection
import org.jetbrains.intellij.build.devDist.planPluginPacking
import org.jetbrains.intellij.build.devDist.pluginPackingExecutionVersion
import org.junit.jupiter.api.Test

internal class DevPluginNativeTreeAssetTest {
  private val jar = PluginPackingAsset(destination = "lib/modules/intellij.libraries.jna.jar", inputs = listOf("module:intellij.libraries.jna"))
  private val nativeTree = PluginPackingAsset(
    destination = "lib/jna",
    inputs = listOf(NATIVE_TREE_INPUT_PREFIX + "intellij.libraries.jna"),
    kind = "tree",
    classPath = false,
  )

  @Test
  fun `native tree in the plugin requires execution version two`() {
    val assets = listOf(jar, nativeTree)

    assertThat(pluginPackingExecutionVersion(assets)).isEqualTo(2)
    assertThat(pluginPackingExecutionVersion(listOf(jar))).isEqualTo(1)
    val plan = planPluginPacking(
      plugin = "intellij.jna.plugin",
      variant = "linux_x64",
      assets = assets,
      operations = emptyList(),
      preparationRoots = emptyList(),
      artifacts = emptyList(),
    )
    assertThat(plan.assets.map { it.asset.destination }).containsExactly("lib/modules/intellij.libraries.jna.jar", "lib/jna")
  }

  @Test
  fun `retired execution version three is refused`() {
    val plan = planPluginPacking(
      plugin = "intellij.jna.plugin",
      variant = "linux_x64",
      assets = listOf(jar, nativeTree),
      operations = emptyList(),
      preparationRoots = emptyList(),
      artifacts = emptyList(),
    )
    val projection = PluginPackingProjection(
      version = 3,
      plugin = plan.plugin,
      variant = plan.variant,
      assets = listOf(jar, nativeTree),
    )

    assertThatThrownBy { projection.plan() }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Unsupported plugin projection version: 3")
  }

  @Test
  fun `plan file asset with a scope is refused`() {
    val text = """
      {"version": 2, "plugin": "intellij.jna.plugin", "variant": "linux_x64", "assets": [
        {"destination": "lib/jna", "inputs": ["native-tree:intellij.libraries.jna"], "kind": "tree", "classPath": false, "scope": "distribution"}
      ]}
    """.trimIndent()

    assertThatThrownBy { Json.decodeFromString(PluginPackingProjection.serializer(), text) }
      .isInstanceOf(SerializationException::class.java)
      .hasMessageContaining("scope")
  }
}
