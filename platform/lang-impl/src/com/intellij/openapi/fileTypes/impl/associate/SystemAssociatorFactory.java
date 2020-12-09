// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl.associate;

import com.intellij.openapi.fileTypes.impl.associate.linux.LinuxFileTypeAssociator;
import com.intellij.openapi.fileTypes.impl.associate.macos.MacOSFileTypeAssociator;
import com.intellij.openapi.fileTypes.impl.associate.win.WinFileTypeAssociator;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.Nullable;

public final class SystemAssociatorFactory {
  private final static SystemFileTypeAssociator FILE_TYPE_ASSOCIATOR = createAssociator();

  private static @Nullable SystemFileTypeAssociator createAssociator() {
    if (SystemInfo.isLinux) {
      return new LinuxFileTypeAssociator();
    }
    if (SystemInfo.isWindows) {
      return new WinFileTypeAssociator();
    }
    if (SystemInfo.isMac) {
      return new MacOSFileTypeAssociator();
    }
    return null;
  }

  private SystemAssociatorFactory() {
  }

  @Nullable
  public static SystemFileTypeAssociator getAssociator() {
    return FILE_TYPE_ASSOCIATOR;
  }
}
