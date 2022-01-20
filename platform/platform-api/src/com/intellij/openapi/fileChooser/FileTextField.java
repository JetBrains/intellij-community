// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface FileTextField {
  String KEY = "fileTextField";

  JTextField getField();

  /** @deprecated the method is not used in the platform and basically does not belong to this interface */
  @Deprecated(forRemoval = true)
  @ApiStatus.ScheduledForRemoval(inVersion = "2023.1")
  default @Nullable VirtualFile getSelectedFile() { return null; }

  boolean isPopupDisplayed();
}
