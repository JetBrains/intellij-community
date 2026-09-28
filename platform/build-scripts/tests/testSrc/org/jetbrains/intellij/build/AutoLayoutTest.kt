// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.impl.PlatformLayout
import org.jetbrains.intellij.build.impl.PluginLayout
import org.jetbrains.intellij.build.impl.contentModuleJarPath
import org.junit.jupiter.api.Test

internal class AutoLayoutTest {
  @Test
  fun `embedded content module is packed into own jar by default`() {
    assertThat(jarPath(loadingRule = "embedded")).isEqualTo("intellij.test.content.jar")
  }

  @Test
  fun `content module without package needs separate jar without marker`() {
    assertThat(jarPath(loadingRule = null, hasPackageAttribute = false)).isEqualTo("modules/intellij.test.content.jar")
  }

  /**
   * A content module the product's filter refuses is in the taken set although no jar packs it, so the auto layout does
   * not put its output into the main jar. That was candidate C-2 of the dev-build guide.
   */
  @Test
  fun `a taken module is not an auto layout child`() {
    val layout = PluginLayout.pluginAutoWithCustomDirName("demo.plugin") { }
    val taken = mutableSetOf("demo.plugin", "demo.plugin.backend")

    val children = inferredAutoLayoutChildren(
      layout = layout,
      directDependencies = sequenceOf("demo.plugin.backend", "demo.plugin.frontend", "other.module"),
      addedModules = taken,
      platformLayout = PlatformLayout(),
      pluginLayouts = listOf(layout),
    )

    assertThat(children).containsExactly("demo.plugin.frontend")
    assertThat(taken).containsExactlyInAnyOrder("demo.plugin", "demo.plugin.backend", "demo.plugin.frontend")
  }

  private fun jarPath(loadingRule: String?, hasPackageAttribute: Boolean = true): String? {
    return contentModuleJarPath(
      moduleName = "intellij.test.content",
      loadingRule = loadingRule,
      hasCustomPath = false,
      mainJarName = "intellij.test.plugin.jar",
      hasPackageAttribute = { hasPackageAttribute },
      packedIntoSeparateJar = { false },
      frontendSplit = { false },
    )
  }
}
