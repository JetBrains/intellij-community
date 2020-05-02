// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.vcs;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class DiffVcsFacade {
  @NotNull
  public static DiffVcsFacade getInstance() {
    return ServiceManager.getService(DiffVcsFacade.class);
  }

  @NotNull
  public FilePath getFilePath(@NotNull String path) {
    return new LocalFilePath(path, false);
  }

  @NotNull
  public FilePath getFilePath(@NotNull VirtualFile virtualFile) {
    return new LocalFilePath(virtualFile.getPath(), virtualFile.isDirectory());
  }
}
