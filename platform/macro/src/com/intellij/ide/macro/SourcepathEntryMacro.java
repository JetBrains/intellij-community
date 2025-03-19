// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.macro;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Belyaev
 */
public final class SourcepathEntryMacro extends Macro implements PathMacro {
  @Override
  public @NotNull String getName() {
    return "SourcepathEntry";
  }

  @Override
  public @NotNull String getDescription() {
    return IdeCoreBundle.message("macro.sourcepath.entry");
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
    final VirtualFile sourceRoot = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(file);
    if (sourceRoot == null) {
      return null;
    }
    return getPath(sourceRoot);
  }
}
