// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.events;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class VFileDeleteEvent extends VFileEvent {
  private final @NotNull VirtualFile myFile;

  public VFileDeleteEvent(@Nullable Object requestor, @NotNull VirtualFile file, boolean isFromRefresh) {
    super(requestor, isFromRefresh);
    myFile = file;
  }

  @Override
  public @NotNull VirtualFile getFile() {
    return myFile;
  }

  @Override
  public @NonNls String toString() {
    return "VfsEvent[deleted: " + myFile.getUrl() + "]";
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
    return myFile.isValid();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VFileDeleteEvent event = (VFileDeleteEvent)o;

    return myFile.equals(event.myFile);
  }

  @Override
  public int hashCode() {
    return myFile.hashCode();
  }
}