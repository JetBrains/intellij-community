// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.JetBrainsRuntimeDistribution
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.LibcImpl
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.ResolvedDownload
import java.nio.file.Path

interface BundledRuntime {
  val prefix: String
  val build: String

  val version: Int get() = build.takeWhile { it != '.' }.toInt()

  suspend fun getHomeForCurrentOsAndArch(): Path

  /**
   * @return a directory, where only one subdirectory is available: 'jbr', which contains specified JBR
   */
  suspend fun extract(os: OsFamily, arch: JvmArchitecture, libc: LibcImpl, prefix: String = this.prefix): Path

  suspend fun extractTo(os: OsFamily, arch: JvmArchitecture, libc: LibcImpl, destinationDir: Path)

  /**
   * The JBR archive, for reading only: under Bazel it is the preloaded runfile itself rather than a
   * copy of it in the download cache.
   */
  suspend fun resolveArchive(os: OsFamily, arch: JvmArchitecture, libc: LibcImpl, prefix: String = this.prefix): ResolvedDownload

  fun downloadUrlFor(os: OsFamily, arch: JvmArchitecture, libc: LibcImpl, prefix: String = this.prefix): String

  fun archiveName(os: OsFamily, arch: JvmArchitecture, libc: LibcImpl, prefix: String = this.prefix, forceVersionWithUnderscores: Boolean = false): String

  fun executableFilesPatterns(os: OsFamily, distribution: JetBrainsRuntimeDistribution): Sequence<String>
}
