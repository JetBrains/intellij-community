// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build

import org.jetbrains.annotations.NotNull

enum OsFamily {
  WINDOWS("win", BuildOptions.OS_WINDOWS, "Windows"),
  MACOS("mac", BuildOptions.OS_MAC, "macOS"),
  LINUX("unix", BuildOptions.OS_LINUX, "Linux");

  final String distSuffix;
  final String osId;
  final String osName;

  OsFamily(@NotNull String distSuffix, @NotNull osId, @NotNull osName) {
    this.distSuffix = distSuffix;
    this.osId = osId;
    this.osName = osName;
  }
}