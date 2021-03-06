// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VFileCopyEvent extends VFileEvent {
  private final VirtualFile myFile;
  private final VirtualFile myNewParent;
  private final String myNewChildName;

  public VFileCopyEvent(final Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile newParent, @NotNull String newChildName) {
    super(requestor, false);
    myFile = file;
    myNewParent = newParent;
    myNewChildName = newChildName;
  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @NotNull
  public VirtualFile getNewParent() {
    return myNewParent;
  }

  @NotNull
  public String getNewChildName() {
    return myNewChildName;
  }

  @Nullable
  public VirtualFile findCreatedFile() {
    return myNewParent.isValid() ? myNewParent.findChild(myNewChildName) : null;
  }

  @Override
  @NonNls
  public String toString() {
    return "VfsEvent[copy " + myFile +" to " + myNewParent + " as " + myNewChildName +"]";
  }

  @NotNull
  @Override
  protected String computePath() {
    return myNewParent.getPath() + "/" + myNewChildName;
  }

  @NotNull
  @Override
  public VirtualFileSystem getFileSystem() {
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
