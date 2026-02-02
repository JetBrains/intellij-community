// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.collections.immutable.persistentListOf
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.getCrossPlatformOnlyBundledPlugins
import org.jetbrains.intellij.build.impl.getPluginLayoutsByJpsModuleNames
import org.jetbrains.intellij.build.productLayout.ProductModulesLayout
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Tests that verify [PluginDistribution.CROSS_PLATFORM_DIST_ONLY] plugins are properly handled:
 * - Excluded from regular distributions via [getPluginLayoutsByJpsModuleNames]
 * - Only bundled plugins with CROSS_PLATFORM_DIST_ONLY are built for cross-platform distribution
 *
 * These plugins should only be included in a cross-platform distribution zip, not in:
 * - Regular bundled plugins (any OS/arch)
 * - Plugins published to the Marketplace
 */
class CrossPlatformDistOnlyTest {

  @Test
  fun `getPluginLayoutsByJpsModuleNames excludes CROSS_PLATFORM_DIST_ONLY plugins`() {
    val regularPlugin = PluginLayout.pluginAuto(listOf("regular.plugin"))
    val crossPlatformOnlyPlugin = PluginLayout.pluginAuto(listOf("cross.platform.only")) {
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.CROSS_PLATFORM_DIST_ONLY
    }

    val productLayout = mock(ProductModulesLayout::class.java)
    `when`(productLayout.pluginLayouts).thenReturn(persistentListOf(regularPlugin, crossPlatformOnlyPlugin))

    var result = getPluginLayoutsByJpsModuleNames(
      modules = listOf("regular.plugin", "cross.platform.only"),
      productLayout = productLayout,
      toPublish = false,
    )

    assertThat(result).containsExactly(regularPlugin)

    result = getPluginLayoutsByJpsModuleNames(
      modules = listOf("regular.plugin", "cross.platform.only"),
      productLayout = productLayout,
      toPublish = true,
    )

    assertThat(result).containsExactly(regularPlugin)
  }


  @Test
  fun `getCrossPlatformOnlyBundledPlugins returns only bundled CROSS_PLATFORM_DIST_ONLY plugins`() {
    val bundledCrossPlatformPlugin = PluginLayout.pluginAuto(listOf("bundled.cross.platform")) {
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.CROSS_PLATFORM_DIST_ONLY
    }
    val nonBundledCrossPlatformPlugin = PluginLayout.pluginAuto(listOf("non.bundled.cross.platform")) {
      it.bundlingRestrictions.includeInDistribution = PluginDistribution.CROSS_PLATFORM_DIST_ONLY
    }
    val regularBundledPlugin = PluginLayout.pluginAuto(listOf("regular.bundled"))

    val productLayout = mock(ProductModulesLayout::class.java)
    `when`(productLayout.pluginLayouts).thenReturn(persistentListOf(
      bundledCrossPlatformPlugin, nonBundledCrossPlatformPlugin, regularBundledPlugin
    ))

    val productProperties = mock(ProductProperties::class.java)
    `when`(productProperties.productLayout).thenReturn(productLayout)

    val context = mock(BuildContext::class.java)
    `when`(context.productProperties).thenReturn(productProperties)
    // Only "bundled.cross.platform" and "regular.bundled" are bundled
    `when`(context.getBundledPluginModules()).thenReturn(listOf("bundled.cross.platform", "regular.bundled"))

    val result = getCrossPlatformOnlyBundledPlugins(context)

    // Should only return the bundled plugin with CROSS_PLATFORM_DIST_ONLY
    assertThat(result).containsExactly(bundledCrossPlatformPlugin)
  }
}
