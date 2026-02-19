// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public class ContentRootMacro extends Macro implements PathMacro {
  @Override
  public @NotNull String getName() {
    return "ContentRoot";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.content.root");
  }

  @Override
  public String expand(final @NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (project == null || file == null) return null;

    final VirtualFile contentRoot = ProjectFileIndex.getInstance(project).getContentRootForFile(file);
    return contentRoot == null ? null : FileUtil.toSystemDependentName(contentRoot.getPath());
  }
}
