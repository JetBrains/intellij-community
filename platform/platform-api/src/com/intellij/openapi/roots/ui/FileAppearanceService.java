// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public abstract @NotNull CellAppearanceEx empty();

  public abstract @NotNull CellAppearanceEx forVirtualFile(@NotNull VirtualFile file);

  public abstract @NotNull CellAppearanceEx forIoFile(@NotNull File file);

  public abstract @NotNull CellAppearanceEx forInvalidUrl(@NlsSafe @NotNull String url);
}
