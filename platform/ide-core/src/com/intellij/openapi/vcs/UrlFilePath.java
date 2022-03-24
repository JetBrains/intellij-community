// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UrlFilePath extends LocalFilePath {
  public UrlFilePath(@NotNull VirtualFile file) {
    this(file.getUrl(), file.isDirectory());
  }

  public UrlFilePath(@NotNull String url, boolean isDirectory) {
    super(url, isDirectory);
  }

  @Override
  protected @Nullable VirtualFile findFile(@NotNull String path) {
    return VirtualFileManager.getInstance().findFileByUrl(path);
  }

  @Override
  @NotNull
  protected @NonNls String getPath(@NotNull VirtualFile cachedFile) {
    return cachedFile.getUrl();
  }
}
