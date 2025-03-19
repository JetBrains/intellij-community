// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ProjectFileDirMacro extends Macro implements PathMacro {
  @Override
  public @NotNull String getName() {
    return "ProjectFileDir";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.project.file.directory");
  }

  @Override
  public @Nullable String expand(@NotNull DataContext dataContext) {
    final VirtualFile baseDir = PlatformCoreDataKeys.PROJECT_FILE_DIRECTORY.getData(dataContext);
    if (baseDir == null) {
      return null;
    }
    return VfsUtil.virtualToIoFile(baseDir).getPath();
  }
}
