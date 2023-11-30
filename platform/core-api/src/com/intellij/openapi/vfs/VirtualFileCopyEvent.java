// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides data for event which is fired when a virtual file is copied.
 *
 * @see VirtualFileListener#fileCopied(VirtualFileCopyEvent)
 */
public class VirtualFileCopyEvent extends VirtualFileEvent {
  private final VirtualFile myOriginalFile;

  public VirtualFileCopyEvent(@Nullable Object requestor, @NotNull VirtualFile original, @NotNull VirtualFile created) {
    super(requestor, created, created.getParent(), 0, 0);
    myOriginalFile = original;
  }

  /**
   * Returns original file.
   *
   * @return original file.
   */
  public @NotNull VirtualFile getOriginalFile() {
    return myOriginalFile;
  }
}
