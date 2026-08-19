// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildPaths.Companion.COMMUNITY_ROOT
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The checkout side of `OsSpecificDistributionBuilder.copyNativeBinFiles`.
 *
 * Every one of these files is loaded through `PathManager.findBinFile`, which answers `null` when the file is
 * absent, and every caller of it treats `null` as "the feature is unavailable here". So a binary that moves or
 * is renamed does not fail a build - it silently thins a distribution, which is how a dev IDE came to run with
 * no file watcher at all. This test is what makes that loud instead.
 *
 * The assertions are deliberately one-directional: a new native under `community/bin` is fine and needs no edit
 * here, a missing one is not.
 */
class DistNativeBinFilesTest {
  private val binDir: Path = COMMUNITY_ROOT.communityRoot.resolve("bin")

  @Test
  fun `macOS natives are where the distribution builder copies them from`() {
    // one universal binary per file, so the same directory serves both architectures
    assertNativeBinFiles(
      binDir.resolve("mac"),
      // NativeFileWatcherImpl
      "fsnotifier",
      // EnvironmentUtil's shell environment reader
      "printenv",
      // NST (Touch Bar)
      "libnst64.dylib",
      // com.intellij.ui.mac.screenmenu.Menu
      "libmacscreenmenu64.dylib",
    )
  }

  @Test
  fun `Linux natives are where the distribution builder copies them from`() {
    for (arch in JvmArchitecture.ALL) {
      assertNativeBinFiles(binDir.resolve("linux/${arch.dirName}"), "fsnotifier")
    }
  }

  @Test
  fun `Windows natives are where the distribution builder copies them from`() {
    for (arch in JvmArchitecture.ALL) {
      assertNativeBinFiles(
        binDir.resolve("win/${arch.dirName}"),
        "fsnotifier.exe",
        // UpdateInstaller - the launcher depends on the elevator
        "launcher.exe",
        "elevator.exe",
        // WinShellIntegration
        "WinShellIntegrationBridge.dll",
      )
    }
    // the top-level files of `bin/win`, which the builder copies without descending into either arch directory
    assertNativeBinFiles(binDir.resolve("win"), "defender-exclusions.ps1")
  }

  private fun assertNativeBinFiles(directory: Path, vararg names: String) {
    assertThat(directory).isDirectory()
    for (name in names) {
      assertThat(directory.resolve(name)).isRegularFile()
    }
  }
}
