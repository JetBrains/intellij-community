// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public abstract class FileAppearanceService {
  public static FileAppearanceService getInstance() {
    return ApplicationManager.getApplication().getService(FileAppearanceService.class);
  }

  @NotNull
  public abstract CellAppearanceEx empty();

  @NotNull
  public abstract CellAppearanceEx forVirtualFile(@NotNull VirtualFile file);

  @NotNull
  public abstract CellAppearanceEx forIoFile(@NotNull File file);

  @NotNull
  public abstract CellAppearanceEx forInvalidUrl(@NlsSafe @NotNull String url);
}
