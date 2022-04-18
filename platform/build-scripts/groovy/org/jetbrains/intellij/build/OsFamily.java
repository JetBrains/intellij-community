// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build;

import com.intellij.openapi.util.SystemInfoRt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public enum OsFamily {
  WINDOWS(BuildOptions.OS_WINDOWS, "Windows", "win", "windows"),
  MACOS(BuildOptions.OS_MAC, "macOS", "mac", "osx"),
  LINUX(BuildOptions.OS_LINUX, "Linux", "unix", "linux");
  public static final List<OsFamily> ALL = List.of(values());

  public static final OsFamily currentOs;

  static {
    currentOs = SystemInfoRt.isWindows ? WINDOWS :
                SystemInfoRt.isMac ? MACOS :
                SystemInfoRt.isLinux ? LINUX : null;
    if (currentOs == null) {
      throw new IllegalStateException("Unknown OS");
    }
  }

  /** ID of OS used in system properties for {@link BuildOptions} */
  public final String osId;
  /** presentable name of OS */
  public final String osName;
  /** suffix for directory name where OS-specific files are produces */
  public final String distSuffix;
  /** suffix of tar.gz archive containing JBR distribution */
  public final String jbrArchiveSuffix;

  OsFamily(@NotNull String osId, @NotNull String osName, @NotNull String distSuffix, @NotNull String jbrArchiveSuffix) {
    this.osId = osId;
    this.osName = osName;
    this.distSuffix = distSuffix;
    this.jbrArchiveSuffix = jbrArchiveSuffix;
  }
}