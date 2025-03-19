// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  suspend fun extract(prefix: String = this.prefix, os: OsFamily, arch: JvmArchitecture): Path

  suspend fun extractTo(os: OsFamily, destinationDir: Path, arch: JvmArchitecture)

  suspend fun findArchive(prefix: String = this.prefix, os: OsFamily, arch: JvmArchitecture): Path

  fun downloadUrlFor(prefix: String = this.prefix, os: OsFamily, arch: JvmArchitecture): String

  fun archiveName(prefix: String = this.prefix, arch: JvmArchitecture, os: OsFamily, forceVersionWithUnderscores: Boolean = false): String

  fun executableFilesPatterns(os: OsFamily, distribution: JetBrainsRuntimeDistribution): Sequence<String>
}
