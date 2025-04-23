// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import org.jetbrains.intellij.build.JetBrainsRuntimeDistribution
import org.jetbrains.intellij.build.JvmArchitecture
import org.jetbrains.intellij.build.OsFamily
import java.nio.file.Path

interface BundledRuntime {
  val prefix: String
  val build: String

  suspend fun getHomeForCurrentOsAndArch(): Path

  /**
   * @return a directory, where only one subdirectory is available: 'jbr', which contains specified JBR
   */
  suspend fun extract(os: OsFamily, arch: JvmArchitecture, prefix: String = this.prefix): Path

  suspend fun extractTo(os: OsFamily, arch: JvmArchitecture, destinationDir: Path)

  suspend fun findArchive(os: OsFamily, arch: JvmArchitecture, prefix: String = this.prefix): Path

  fun downloadUrlFor(os: OsFamily, arch: JvmArchitecture, prefix: String = this.prefix): String

  fun archiveName(os: OsFamily, arch: JvmArchitecture, prefix: String = this.prefix, forceVersionWithUnderscores: Boolean = false): String

  fun executableFilesPatterns(os: OsFamily, distribution: JetBrainsRuntimeDistribution): Sequence<String>
}
