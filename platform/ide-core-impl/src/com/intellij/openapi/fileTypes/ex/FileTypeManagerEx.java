// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class FileTypeManagerEx extends FileTypeManager {
  public static FileTypeManagerEx getInstanceEx() {
    return (FileTypeManagerEx)getInstance();
  }

  public abstract void freezeFileTypeTemporarilyIn(@NotNull VirtualFile file, @NotNull Runnable runnable);

  public abstract boolean isIgnoredFilesListEqualToCurrent(@NotNull String list);

  public abstract @NotNull String getExtension(@NotNull String fileName);

  /**
   * Use {@link FileTypeManagerEx#makeFileTypesChange(String, Runnable)} instead.
   */
  public abstract void fireFileTypesChanged();

  /**
   * Use {@link FileTypeManagerEx#makeFileTypesChange(String, Runnable)} instead.
   */
  public abstract void fireBeforeFileTypesChanged();

  /**
   * Use this method to notify {@link FileTypeManager} that file type association has been changed.
   */
  public abstract void makeFileTypesChange(@NonNls @NotNull String debugReasonMessage, @NotNull Runnable command);
}
