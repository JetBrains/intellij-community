// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.FileAppearanceService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NotNull;

class TableItem {
  private final String myUrl;
  private final CellAppearanceEx myCellAppearance;

  TableItem(final @NotNull VirtualFile file) {
    myUrl = file.getUrl();
    myCellAppearance = FileAppearanceService.getInstance().forVirtualFile(file);
  }

  TableItem(final @NotNull String url) {
    myUrl = url;

    final VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file != null) {
      myCellAppearance = FileAppearanceService.getInstance().forVirtualFile(file);
    }
    else {
      myCellAppearance = FileAppearanceService.getInstance().forInvalidUrl(url);
    }
  }

  public @NotNull String getUrl() {
    return myUrl;
  }

  public @NotNull CellAppearanceEx getCellAppearance() {
    return myCellAppearance;
  }
}
