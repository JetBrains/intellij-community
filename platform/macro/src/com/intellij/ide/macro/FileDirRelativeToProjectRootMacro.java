// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class FileDirRelativeToProjectRootMacro extends Macro {
  @Override
  public @NotNull String getName() {
    return "FileDirRelativeToProjectRoot";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.file.dir.relative.to.root");
  }

  @Override
  public String expand(final @NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return null;
    }
    VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null) {
      return null;
    }
    if (!file.isDirectory()) {
      file = file.getParent();
      if (file == null) {
        return null;
      }
    }

    VirtualFile contentRoot = ProjectRootManager.getInstance(project).getFileIndex().getContentRootForFile(file);
    if (contentRoot != null && contentRoot.isDirectory()) {
      return FileUtil.getRelativePath(getIOFile(contentRoot), getIOFile(file));
    }

    final VirtualFile baseDirectory = BaseProjectDirectories.getInstance(project).getBaseDirectoryFor(file);
    if (baseDirectory != null) {
      return FileUtil.getRelativePath(getIOFile(baseDirectory), getIOFile(file));
    }

    return null;
  }
}
