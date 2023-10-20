// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VFileCopyEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final VirtualFile myNewParent;
  private final String myNewChildName;

  @ApiStatus.Internal
  public VFileCopyEvent(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String newChildName) {
    super(requestor);
    myFile = file;
    myNewParent = newParent;
    myNewChildName = newChildName;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  public @NotNull VirtualFile getNewParent() {
    return myNewParent;
  }

  public @NotNull String getNewChildName() {
    return myNewChildName;
  }

  public @Nullable VirtualFile findCreatedFile() {
    return myNewParent.isValid() ? myNewParent.findChild(myNewChildName) : null;
  }

  @Override
  public String toString() {
    return "VfsEvent[copy " + myFile +" to " + myNewParent + " as " + myNewChildName +"]";
  }

  @Override
  protected @NotNull String computePath() {
    return myNewParent.getPath() + "/" + myNewChildName;
  }

  @Override
  public @NotNull VirtualFileSystem getFileSystem() {
    return myFile.getFileSystem();
  }

  @Override
  public boolean isValid() {
    return myFile.isValid() && myNewParent.findChild(myNewChildName) == null;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileCopyEvent event = (VFileCopyEvent)o;

    if (!myFile.equals(event.myFile)) return false;
    if (!myNewChildName.equals(event.myNewChildName)) return false;
    if (!myNewParent.equals(event.myNewParent)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myFile.hashCode();
    result = 31 * result + myNewParent.hashCode();
    result = 31 * result + myNewChildName.hashCode();
    return result;
  }
}
