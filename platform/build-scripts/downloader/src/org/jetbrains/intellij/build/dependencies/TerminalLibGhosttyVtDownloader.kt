// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.resolveAndExtractToCacheLocation
import java.nio.file.Path

@ApiStatus.Internal
object TerminalLibGhosttyVtDownloader {
  private const val LIB_GHOSTTY_VT = "libghostty-vt"

  /**
   * Downloads the library archive and returns its extracted root, which holds the
   * `<os>-<arch>` subdirectories for all platforms.
   *
   * If it was downloaded previously, network I/O is skipped and the result
   * is returned from cache (`ultimate/community/build/download`).
   *
   * See `community/plugins/terminal/emulator/README.md` for the update procedure.
   */
  fun getOrDownloadLibRoot(communityRoot: BuildDependenciesCommunityRoot): Path {
    val version = BuildDependenciesDownloader.getDependencyProperties(communityRoot).property("libGhosttyVtVersion")
    return runBlocking {
      resolveAndExtractToCacheLocation(downloadUrl(version), communityRoot)
    }
  }

  /** Public so tests that pre-provision the build-dependencies download cache can pin the same URL. */
  fun downloadUrl(version: String): String =
    "https://packages.jetbrains.team/files/p/ij/intellij-build-dependencies/$LIB_GHOSTTY_VT/$version/$LIB_GHOSTTY_VT.zip.zst"
}
