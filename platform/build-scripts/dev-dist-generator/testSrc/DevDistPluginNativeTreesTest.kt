// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicArtifact
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicArtifactCatalogue
import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.intellij.build.devDist.NATIVE_TREE_INPUT_PREFIX
import org.jetbrains.intellij.build.devDist.PluginPackingAsset
import org.jetbrains.intellij.build.devDist.PluginPackingProjection
import org.jetbrains.intellij.build.devDist.ReusableJarArtifact
import org.jetbrains.intellij.build.devDist.pluginPackingExecutionVersion
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.productLayout.JNA_NATIVE_DIR
import org.jetbrains.intellij.build.productLayout.JNA_PLUGIN_MODULE
import org.junit.jupiter.api.Test

/**
 * The check of the presigned native trees in the plugin plans, see [checkPluginNativeTrees].
 *
 * A native tree sits in the `lib/` directory of the plugin that owns its natives jar. Two plugins of one product must
 * not pack the same tree. The launcher path of the JNA tree must be the path that the plan gives.
 */
class DevDistPluginNativeTreesTest {
  /** The tree destination that two synthetic plugins share in the duplicate case. */
  private val treeDestination = "lib/synthetic-natives"

  /** The native tree asset that the reused natives jar of [module] writes at [destination]. */
  private fun nativeTree(destination: String, module: String = "intellij.libraries.synthetic.natives"): PluginPackingAsset {
    return PluginPackingAsset(destination = destination, inputs = listOf("$NATIVE_TREE_INPUT_PREFIX$module"), kind = "tree", classPath = false)
  }

  /** One neutral plan entry of [plugin] in [product] over [assets]. */
  private fun planEntry(plugin: String, assets: List<PluginPackingAsset>, product: String = "idea"): DevDistPluginPlanEntry {
    val plan = object : DevDistPluginBuildPlan {
      override val projection = PluginPackingProjection(
        version = pluginPackingExecutionVersion(assets),
        plugin = plugin,
        variant = "",
        assets = assets,
        operations = emptyList(),
      )
      override val catalogue = PluginSymbolicArtifactCatalogue(
        artifacts = listOf(PluginSymbolicArtifact(id = plugin, kind = "directory", fileName = plugin)),
        moduleRoots = mapOf(plugin to listOf(plugin)),
        libraries = emptyList(),
        testModules = emptySet(),
      )
      override val requiredRawInputs = emptyList<DevDistPluginRawInput>()
      override val requiredLibraries = emptyList<String>()
      override val reusableArtifacts = emptyList<ReusableJarArtifact>()
    }
    return DevDistPluginPlanEntry(
      product = product,
      tier = DevDistPluginTier.BUNDLED,
      mainModule = plugin,
      layouts = mapOf("" to PluginLayout.plugin(plugin)),
      records = mapOf("" to DevDistPluginPlanRecord(variant = PluginSymbolicVariant(id = ""), plan = plan)),
    )
  }

  /** The tree destination of the JNA plugin plan that gives the launcher path [JNA_NATIVE_DIR]. */
  private fun jnaTreeDestination(): String {
    val prefix = "plugins/${PluginLayout.plugin(JNA_PLUGIN_MODULE).directoryName}/"
    assertThat(JNA_NATIVE_DIR).startsWith(prefix)
    return JNA_NATIVE_DIR.removePrefix(prefix)
  }

  @Test
  fun `two plugins of one product that pack the same native tree fail and the message names both plugins`() {
    val first = planEntry("intellij.synthetic.first", listOf(nativeTree(treeDestination)))
    val second = planEntry("intellij.synthetic.second", listOf(nativeTree(treeDestination)))

    assertThatThrownBy { checkPluginNativeTrees(listOf(first, second)) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("idea/$treeDestination")
      .hasMessageContaining("intellij.synthetic.first")
      .hasMessageContaining("intellij.synthetic.second")
  }

  @Test
  fun `one owner of a native tree passes`() {
    val owner = planEntry("intellij.synthetic.first", listOf(nativeTree(treeDestination)))
    val other = planEntry("intellij.synthetic.second", listOf(nativeTree("lib/other-natives", module = "intellij.libraries.other.natives")))

    assertThatCode { checkPluginNativeTrees(listOf(owner, other)) }.doesNotThrowAnyException()
  }

  @Test
  fun `the same native tree in two products passes`() {
    val inIdea = planEntry("intellij.synthetic.first", listOf(nativeTree(treeDestination)), product = "idea")
    val inOther = planEntry("intellij.synthetic.second", listOf(nativeTree(treeDestination)), product = "other")

    assertThatCode { checkPluginNativeTrees(listOf(inIdea, inOther)) }.doesNotThrowAnyException()
  }

  @Test
  fun `the JNA plugin passes when its plan gives the launcher path`() {
    val jna = planEntry(JNA_PLUGIN_MODULE, listOf(nativeTree(jnaTreeDestination(), module = "intellij.libraries.jna")))

    assertThatCode { checkPluginNativeTrees(listOf(jna)) }.doesNotThrowAnyException()
  }

  @Test
  fun `the JNA plugin fails when its plan places the tree away from the launcher path`() {
    val jna = planEntry(JNA_PLUGIN_MODULE, listOf(nativeTree("lib/misplaced", module = "intellij.libraries.jna")))

    assertThatThrownBy { checkPluginNativeTrees(listOf(jna)) }
      .isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining(JNA_NATIVE_DIR)
      .hasMessageContaining("lib/misplaced")
  }
}
