// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

public abstract class RefreshSession {
  public abstract void addFile(@NotNull VirtualFile file);

  public abstract void addAllFiles(@NotNull Collection<? extends @NotNull VirtualFile> files);

  public void addAllFiles(@NotNull VirtualFile @NotNull ... files) {
    addAllFiles(List.of(files));
  }

  public abstract void launch();

  public abstract void cancel();

  @ApiStatus.Internal
  public abstract Object metric(@NotNull String key);
}
