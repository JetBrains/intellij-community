// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebook.editor;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface BackedVirtualFileProvider {
  ExtensionPointName<BackedVirtualFileProvider> EP_NAME = ExtensionPointName.create("com.intellij.backedVirtualFileProvider");

  /**
   * @return custom (BackedVirtualFile) if there is one that was build on the top of the original VirtualFile, null otherwise.
   *
   * For example, Jupyter Notebook JSON file replaced with its text representation
   */
  @Nullable VirtualFile getReplacedVirtualFile(@NotNull VirtualFile originalFile);
}
