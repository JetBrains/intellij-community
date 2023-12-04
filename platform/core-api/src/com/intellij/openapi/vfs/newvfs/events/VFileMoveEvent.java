// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class VFileMoveEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final VirtualFile myOldParent;
  private final VirtualFile myNewParent;

  @ApiStatus.Internal
  public VFileMoveEvent(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent) {
    super(requestor);
    myFile = file;
    myNewParent = newParent;
    myOldParent = file.getParent();
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  public @NotNull VirtualFile getNewParent() {
    return myNewParent;
  }

  public VirtualFile getOldParent() {
    return myOldParent;
  }

  @Override
  public String toString() {
    return "VfsEvent[move " + myFile.getName() +" from " + myOldParent + " to " + myNewParent + "]";
  }

  @Override
  public @NotNull String getPath() {
    return computePath();
  }

  @Override
  protected @NotNull String computePath() {
    return myFile.getPath();
  }

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  @Override
  public boolean isValid() {
    return myFile.isValid() && Comparing.equal(myFile.getParent(), myOldParent) && myOldParent.isValid();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileMoveEvent event = (VFileMoveEvent)o;

    if (!myFile.equals(event.myFile)) return false;
    if (!myNewParent.equals(event.myNewParent)) return false;
    if (!myOldParent.equals(event.myOldParent)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + myOldParent.hashCode();
    result = 31 * result + myNewParent.hashCode();
    return result;
  }

  public @NotNull String getOldPath() {
    return myOldParent.getPath() + "/" + myFile.getName();
  }

  public @NotNull String getNewPath() {
    return myNewParent.getPath() + "/" + myFile.getName();
  }
}
