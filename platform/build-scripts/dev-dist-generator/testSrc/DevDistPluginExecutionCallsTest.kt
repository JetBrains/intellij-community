// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildScripts.devDistGenerator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class DevDistPluginExecutionCallsTest {
  private val mainModule = "intellij.sample"
  private val platforms = listOf("darwin_aarch64", "linux_x64")

  /** The macro reads the folded plan file when the call states no `platform_plans`, so a fold without a slot states neither. */
  @Test
  fun `a folded plan without a slot states neither platform_values nor platform_plans`() {
    val text = renderDevDistPluginExecutionCalls(mainModule, platforms.map { variant(it, folded = true) })

    assertThat(text).isEqualTo(
      """
      dev_dist_complex_plugin(
          descriptor = "//plugins/sample:descriptor_{platform}",
          main_module = "intellij.sample",
          platforms = [
              "darwin_aarch64",
              "linux_x64",
          ],
      )
      """.trimIndent() + "\n"
    )
  }

  @Test
  fun `a folded plan with a slot states the slot value of each platform`() {
    val variants = platforms.map { variant(it, folded = true, values = mapOf("destination" to "lib/$it")) }

    val text = renderDevDistPluginExecutionCalls(mainModule, variants)

    assertThat(text).isEqualTo(
      """
      dev_dist_complex_plugin(
          descriptor = "//plugins/sample:descriptor_{platform}",
          main_module = "intellij.sample",
          platform_values = {
              "darwin_aarch64": {
                  "destination": "lib/darwin_aarch64",
              },
              "linux_x64": {
                  "destination": "lib/linux_x64",
              },
          },
          platforms = [
              "darwin_aarch64",
              "linux_x64",
          ],
      )
      """.trimIndent() + "\n"
    )
  }

  /** A refused fold keeps a plan file per platform. `platform_plans` tells the macro to derive those file names. */
  @Test
  fun `a refused fold states platform_plans and keeps the platforms`() {
    val text = renderDevDistPluginExecutionCalls(mainModule, platforms.map { variant(it, folded = false) })

    assertThat(text).isEqualTo(
      """
      dev_dist_complex_plugin(
          descriptor = "//plugins/sample:descriptor_{platform}",
          main_module = "intellij.sample",
          platform_plans = True,
          platforms = [
              "darwin_aarch64",
              "linux_x64",
          ],
      )
      """.trimIndent() + "\n"
    )
  }

  @Test
  fun `a neutral chain states neither platforms nor platform_values nor platform_plans`() {
    val text = renderDevDistPluginExecutionCalls(mainModule, listOf(variant(platform = null, folded = false)))

    assertThat(text).isEqualTo(
      """
      dev_dist_complex_plugin(
          descriptor = "//plugins/sample:descriptor",
          main_module = "intellij.sample",
      )
      """.trimIndent() + "\n"
    )
  }

  @Test
  fun `folded and unfolded chains of one plugin fail the render`() {
    val variants = listOf(variant("darwin_aarch64", folded = true), variant("linux_x64", folded = false))

    assertThatThrownBy { renderDevDistPluginExecutionCalls(mainModule, variants) }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("mixes folded and unfolded plan files")
  }

  private fun variant(platform: String?, folded: Boolean, values: Map<String, String> = emptyMap()): DevDistPluginExecutionVariant {
    val descriptor = if (platform == null) "//plugins/sample:descriptor" else "//plugins/sample:descriptor_$platform"
    return DevDistPluginExecutionVariant(
      platform = platform,
      arguments = listOf("main_module" to "\"$mainModule\"", "descriptor" to "\"$descriptor\""),
      componentLabel = "//plugins/sample:${devDistChainStem(mainModule, chainClass = "", platform = platform)}_component",
      platformValues = values,
      folded = folded,
    )
  }
}
