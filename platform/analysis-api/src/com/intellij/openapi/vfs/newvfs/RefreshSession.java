// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public abstract class RefreshSession {
  public long getId() {
    return 0;
  }

  public abstract boolean isAsynchronous();

  public abstract void addFile(@NotNull VirtualFile file);

  public abstract void addAllFiles(@NotNull Collection<? extends @NotNull VirtualFile> files);

  public void addAllFiles(@NotNull VirtualFile @NotNull ... files) {
    addAllFiles(Arrays.asList(files));
  }

  public abstract void launch();
}
