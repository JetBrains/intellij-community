// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.tree;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ProjectContentFileFilter implements VirtualFileFilter {
  private final Project project;
  private final VirtualFileFilter filter;

  private ProjectFileIndex fileIndex;

  public ProjectContentFileFilter(@NotNull Project project, @NotNull VirtualFileFilter filter) {
    this.project = project;
    this.filter = filter;
  }

  @Override
  public boolean accept(@NotNull VirtualFile file) {
    if (!filter.accept(file)) {
      return false;
    }

    if (fileIndex == null) {
      fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }
    return fileIndex.isInContent(file);
  }
}
