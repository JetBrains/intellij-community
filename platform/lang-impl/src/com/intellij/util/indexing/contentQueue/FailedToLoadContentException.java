// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.contentQueue;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public final class FailedToLoadContentException extends Exception {
  private final VirtualFile myFile;

  public FailedToLoadContentException(@NotNull VirtualFile file, @NotNull Throwable cause) {
    super(cause);
    myFile = file;
  }

  public @NotNull VirtualFile getFile() {
    return myFile;
  }
}