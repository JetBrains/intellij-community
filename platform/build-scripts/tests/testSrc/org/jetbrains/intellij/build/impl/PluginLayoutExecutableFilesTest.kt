// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.OsFamily
import org.junit.jupiter.api.Test

class PluginLayoutExecutableFilesTest {
  @Test
  fun `a distribution ships the files that a declaration names for it, in the order of the declarations`() {
    val unix = SUPPORTED_DISTRIBUTIONS.filter { it.os != OsFamily.WINDOWS }
    val linux = SUPPORTED_DISTRIBUTIONS.filter { it.os == OsFamily.LINUX }
    val layout = PluginLayout.pluginAuto(listOf("intellij.example")) { spec ->
      spec.withExecutable("bin/tool", unix)
      spec.withExecutable("bin/linux/*", linux)
      spec.withExecutable("bin/everywhere")
    }

    for (distribution in SUPPORTED_DISTRIBUTIONS) {
      val expected = when (distribution.os) {
        OsFamily.WINDOWS -> listOf("bin/everywhere")
        OsFamily.MACOS -> listOf("bin/tool", "bin/everywhere")
        OsFamily.LINUX -> listOf("bin/tool", "bin/linux/*", "bin/everywhere")
      }
      assertThat(layout.executableFiles(distribution)).describedAs("$distribution").containsExactlyElementsOf(expected)
    }
  }
}
