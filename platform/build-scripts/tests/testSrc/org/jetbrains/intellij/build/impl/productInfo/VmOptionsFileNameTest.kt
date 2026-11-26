// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.OsFamily
import org.junit.jupiter.api.Test

/**
 * The names of the main vmoptions file. `vm_options_file_name_test` of `community/build/dev_run_configuration_test.bzl` pins
 * the same cases of `vm_options_file_name`, which the Bazel declarations derive the name with.
 */
internal class VmOptionsFileNameTest {
  @Test
  fun `an IDE adds 64 on Linux and Windows`() {
    assertNames("idea", languageServer = false, macOs = "idea.vmoptions", linux = "idea64.vmoptions", windows = "idea64.exe.vmoptions")
    assertNames(
      "jetbrains_client",
      languageServer = false,
      macOs = "jetbrains_client.vmoptions",
      linux = "jetbrains_client64.vmoptions",
      windows = "jetbrains_client64.exe.vmoptions",
    )
  }

  @Test
  fun `a language server adds no 64`() {
    assertNames(
      "intellij-server",
      languageServer = true,
      macOs = "intellij-server.vmoptions",
      linux = "intellij-server.vmoptions",
      windows = "intellij-server.exe.vmoptions",
    )
  }

  private fun assertNames(baseFileName: String, languageServer: Boolean, macOs: String, linux: String, windows: String) {
    assertThat(vmOptionsFileName(OsFamily.MACOS, baseFileName, languageServer)).isEqualTo(macOs)
    assertThat(vmOptionsFileName(OsFamily.LINUX, baseFileName, languageServer)).isEqualTo(linux)
    assertThat(vmOptionsFileName(OsFamily.WINDOWS, baseFileName, languageServer)).isEqualTo(windows)
  }
}
