// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.distGenerator

import com.intellij.platform.buildScripts.pluginModelTool.PluginSymbolicVariant
import com.intellij.platform.pluginGraph.PluginId
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.SUPPORTED_DISTRIBUTIONS
import org.jetbrains.intellij.build.productLayout.TestPluginSpec
import org.jetbrains.intellij.build.productLayout.productModules
import org.junit.jupiter.api.Test

/** `DEV_DIST_TEST_PLUGINS`: the main modules of the test plugins in the component tiers of each product. */
class DistTestPluginsTest {
  private val linuxX64 = PluginSymbolicVariant(
    id = "linux_x64",
    distribution = SUPPORTED_DISTRIBUTIONS.single { it.os == OsFamily.LINUX && it.arch == JvmArchitecture.x64 },
  )
  private val properties = SyntheticProductProperties()

  private fun request(product: String, mainModule: String, tier: DistPluginTier, isTestPlugin: Boolean): DistPluginRequest {
    val testPlugin = if (isTestPlugin) {
      TestPluginSpec(PluginId(mainModule), name = mainModule, pluginXmlPath = "", spec = productModules { })
    }
    else {
      null
    }
    return DistPluginRequest(
      product = product,
      properties = properties,
      tier = tier,
      variant = linuxX64,
      layout = PluginLayout.pluginWithCustomLayout(mainModule) { },
      testPlugin = testPlugin,
    )
  }

  private fun components(bundled: List<String>, additional: List<String>): Map<DistPluginTier, List<GeneratedPluginComponent>> {
    return mapOf(
      DistPluginTier.BUNDLED to bundled.map { GeneratedPluginComponent(it, label = "//$it:c") },
      DistPluginTier.ADDITIONAL to additional.map { GeneratedPluginComponent(it, label = "//$it:c") },
    )
  }

  @Test
  fun `the table lists the test plugins of each product sorted, and a product without one has no key`() {
    val requests = listOf(
      request("idea", "intellij.java.plugin", DistPluginTier.BUNDLED, isTestPlugin = false),
      request("idea", "intellij.devkit", DistPluginTier.ADDITIONAL, isTestPlugin = false),
      request("idea", "intellij.z.test", DistPluginTier.ADDITIONAL, isTestPlugin = true),
      request("idea", "intellij.a.test", DistPluginTier.ADDITIONAL, isTestPlugin = true),
      request("go", "intellij.go", DistPluginTier.BUNDLED, isTestPlugin = false),
      request("Rider", "intellij.rider.test", DistPluginTier.ADDITIONAL, isTestPlugin = true),
    )
    val components = mapOf(
      "idea" to components(
        bundled = listOf("intellij.java.plugin"),
        additional = listOf("intellij.devkit", "intellij.z.test", "intellij.a.test"),
      ),
      "go" to components(bundled = listOf("intellij.go"), additional = emptyList()),
      "Rider" to components(bundled = emptyList(), additional = listOf("intellij.rider.test")),
    )

    val testPlugins = distTestPluginModules(requests, components, communityProducts = emptySet())

    assertThat(testPlugins).containsExactly(
      java.util.Map.entry("Rider", listOf("intellij.rider.test")),
      java.util.Map.entry("idea", listOf("intellij.a.test", "intellij.z.test")),
    )
    assertThat(renderTestPlugins(testPlugins)).isEqualTo(
      """
      DEV_DIST_TEST_PLUGINS = {
          "Rider": [
              "intellij.rider.test",
          ],
          "idea": [
              "intellij.a.test",
              "intellij.z.test",
          ],
      }

      """.trimIndent()
    )
  }

  @Test
  fun `a registry plugin and the bundled tier of a community product are not in the table`() {
    val requests = listOf(
      // A registry plugin has no component, so it is in no tier.
      request("idea", "intellij.registry.test", DistPluginTier.REGISTRY, isTestPlugin = true),
      request("idea", "intellij.additional.test", DistPluginTier.ADDITIONAL, isTestPlugin = true),
      // `DEV_DIST_PLUGIN_COMPONENTS` states no bundled tier of a community product.
      request("Idea", "intellij.bundled.test", DistPluginTier.BUNDLED, isTestPlugin = true),
      request("Idea", "intellij.community.test", DistPluginTier.ADDITIONAL, isTestPlugin = true),
    )
    val components = mapOf(
      "idea" to components(bundled = emptyList(), additional = listOf("intellij.additional.test")),
      "Idea" to components(bundled = listOf("intellij.bundled.test"), additional = listOf("intellij.community.test")),
    )

    assertThat(distTestPluginModules(requests, components, communityProducts = setOf("Idea"))).containsExactly(
      java.util.Map.entry("Idea", listOf("intellij.community.test")),
      java.util.Map.entry("idea", listOf("intellij.additional.test")),
    )
  }

  @Test
  fun `a run without a test plugin renders no table`() {
    val requests = listOf(request("Idea", "intellij.java.plugin", DistPluginTier.BUNDLED, isTestPlugin = false))
    val components = mapOf("Idea" to components(bundled = listOf("intellij.java.plugin"), additional = emptyList()))

    val testPlugins = distTestPluginModules(requests, components, communityProducts = emptySet())

    assertThat(testPlugins).isEmpty()
    assertThat(renderTestPlugins(testPlugins)).isEmpty()
  }
}
