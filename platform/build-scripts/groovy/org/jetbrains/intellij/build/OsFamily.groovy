// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import com.intellij.openapi.util.SystemInfoRt
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull

@CompileStatic
enum OsFamily {
  WINDOWS(BuildOptions.OS_WINDOWS, "Windows", "win", "windows"),
  MACOS(BuildOptions.OS_MAC, "macOS", "mac", "osx"),
  LINUX(BuildOptions.OS_LINUX, "Linux", "unix", "linux");
  static final List<OsFamily> ALL = List.of(values())

  static final OsFamily currentOs;

  static {
    currentOs = SystemInfoRt.isWindows ? WINDOWS :
                SystemInfoRt.isMac ? MACOS :
                SystemInfoRt.isLinux ? LINUX : null
    if (currentOs == null) {
      throw new IllegalStateException("Unknown OS")
    }
  }

  /** ID of OS used in system properties for {@link BuildOptions} */
  final String osId
  /** presentable name of OS */
  final String osName
  /** suffix for directory name where OS-specific files are produces */
  final String distSuffix
  /** suffix of tar.gz archive containing JBR distribution */
  final String jbrArchiveSuffix

  private OsFamily(@NotNull String osId, @NotNull String osName, @NotNull String distSuffix, @NotNull String jbrArchiveSuffix) {
    this.osId = osId
    this.osName = osName
    this.distSuffix = distSuffix
    this.jbrArchiveSuffix = jbrArchiveSuffix
  }
}