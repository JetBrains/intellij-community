// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit.actions.associate;

import com.intellij.ide.lightEdit.actions.associate.linux.LinuxFileTypeAssociator;
import com.sun.jna.Platform;
import org.jetbrains.annotations.Nullable;

public final class SystemAssociatorFactory {
  private final static SystemFileTypeAssociator FILE_TYPE_ASSOCIATOR;
  static {
    if (Platform.isLinux()) {
      FILE_TYPE_ASSOCIATOR = new LinuxFileTypeAssociator();
    }
    else {
      FILE_TYPE_ASSOCIATOR = null;
    }
  }

  private SystemAssociatorFactory() {
  }

  @Nullable
  public static SystemFileTypeAssociator getAssociator() {
    return FILE_TYPE_ASSOCIATOR;
  }
}
