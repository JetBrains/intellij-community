// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.pointers;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A pointer to a {@link VirtualFile}.
 *
 * @see VirtualFilePointerManager#create
 * @see VirtualFilePointerContainer
 */
public interface VirtualFilePointer {
  VirtualFilePointer[] EMPTY_ARRAY = new VirtualFilePointer[0];

  @NotNull
  String getFileName();

  @Nullable
  VirtualFile getFile();

  @NotNull
  @NonNls String getUrl();

  @NlsSafe @NotNull String getPresentableUrl();

  /**
   * @return true if the file exists
   */
  boolean isValid();
}
