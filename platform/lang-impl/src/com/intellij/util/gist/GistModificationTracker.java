// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.gist;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public final class GistModificationTracker implements ModificationTracker {
  private final @NotNull VirtualFile myFile;

  public GistModificationTracker(@NotNull VirtualFile file) { myFile = file; }

  @Override
  public long getModificationCount() {
    // non-negative for com.intellij.util.CachedValueBase.isDependencyOutOfDate
    return Math.abs(GistManagerImpl.getGistStamp(myFile));
  }
}
