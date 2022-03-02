// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.vcs;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.UrlFilePath;
import com.intellij.openapi.vfs.VersionedFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class DiffVcsFacade {
  @NotNull
  public static DiffVcsFacade getInstance() {
    return ApplicationManager.getApplication().getService(DiffVcsFacade.class);
  }

  @NotNull
  public FilePath getFilePath(@NotNull @NonNls String path) {
    return path.contains(URLUtil.SCHEME_SEPARATOR)
           ? new UrlFilePath(path, false)
           : new LocalFilePath(path, false);
  }

  @NotNull
  public FilePath getFilePath(@NotNull VirtualFile virtualFile) {
    return virtualFile.getFileSystem() instanceof VersionedFileSystem
           ? new UrlFilePath(virtualFile.getUrl(), virtualFile.isDirectory())
           : new LocalFilePath(virtualFile.getPath(), virtualFile.isDirectory());
  }
}
