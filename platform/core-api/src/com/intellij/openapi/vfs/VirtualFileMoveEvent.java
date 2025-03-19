// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides data for an event that is fired when a virtual file is moved.
 *
 * @see VirtualFileListener#beforePropertyChange(VirtualFilePropertyEvent)
 * @see VirtualFileListener#propertyChanged(VirtualFilePropertyEvent)
 */
public class VirtualFileMoveEvent extends VirtualFileEvent {
  private final VirtualFile myOldParent;
  private final VirtualFile myNewParent;

  public VirtualFileMoveEvent(@Nullable Object requestor,
                              @NotNull VirtualFile file,
                              @NotNull VirtualFile oldParent,
                              @NotNull VirtualFile newParent) {
    super(requestor, file, file.getParent(), 0, 0);
    myOldParent = oldParent;
    myNewParent = newParent;
  }

  /**
   * Returns the parent of the file before the move.
   *
   * @return the parent of the file before the move.
   */
  public @NotNull VirtualFile getOldParent() {
    return myOldParent;
  }

  /**
   * Returns the parent of the file after the move.
   *
   * @return the parent of the file after the move.
   */
  public @NotNull VirtualFile getNewParent() {
    return myNewParent;
  }
}