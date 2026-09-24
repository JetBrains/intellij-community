// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
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
