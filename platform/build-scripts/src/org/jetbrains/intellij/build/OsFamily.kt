// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfoRt
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

enum class OsFamily(
  /** ID of OS used in system properties for [BuildOptions] */
  @JvmField
  val osId: String,
  /** presentable name of OS */
  @JvmField
  val osName: String,
  /** directory name in cross-platform distribution and various temporary locations */
  @JvmField
  val dirName: String,
  /** suffix for directory name where OS-specific files are produces */
  @JvmField
  val distSuffix: String,
  /** suffix of tar.gz archive containing JBR distribution */
  @JvmField
  val jbrArchiveSuffix: String
) {
  WINDOWS(osId = BuildOptions.OS_WINDOWS, osName = "Windows", dirName = "win", distSuffix = "win", jbrArchiveSuffix = "windows"),
  MACOS(osId = BuildOptions.OS_MAC, osName = "macOS", dirName = "mac", distSuffix = "mac", jbrArchiveSuffix = "osx"),
  LINUX(osId = BuildOptions.OS_LINUX, osName = "Linux", dirName = "linux", distSuffix = "unix", jbrArchiveSuffix = "linux");

  companion object {
    @JvmField
    val ALL: PersistentList<OsFamily> = persistentListOf(*OsFamily.entries.toTypedArray())

    @JvmField
    val currentOs: OsFamily = when {
      SystemInfoRt.isWindows -> WINDOWS
      SystemInfoRt.isMac -> MACOS
      SystemInfoRt.isLinux -> LINUX
      else -> throw IllegalStateException("Unknown OS")
    }
  }
}
