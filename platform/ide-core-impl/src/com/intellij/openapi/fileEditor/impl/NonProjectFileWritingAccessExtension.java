// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface NonProjectFileWritingAccessExtension {
  ExtensionPointName<NonProjectFileWritingAccessExtension> EP_NAME =
    ExtensionPointName.create("com.intellij.nonProjectFileWritingAccessExtension");

  /**
   * @return true if the file should not be protected from accidental writing. false to use default logic.
   */
  default boolean isWritable(@NotNull VirtualFile file) {
    return false;
  }

  /**
   * @return true if the file should be protected from accidental writing. false to use default logic.
   */
  default boolean isNotWritable(@NotNull VirtualFile file) {
    return false;
  }
}
