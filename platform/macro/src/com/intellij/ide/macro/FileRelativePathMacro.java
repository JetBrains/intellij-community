// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class FileRelativePathMacro extends Macro {
  @Override
  public @NotNull String getName() {
    return "FileRelativePath";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.file.path.relative");
  }

  @Override
  public String expand(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final VirtualFile baseDir = project == null ? null : project.getBaseDir();
    if (baseDir == null) {
      return null;
    }

    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) return null;
    return FileUtil.getRelativePath(VfsUtil.virtualToIoFile(baseDir), VfsUtil.virtualToIoFile(file));
  }
}
