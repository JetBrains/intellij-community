// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope;

import com.intellij.icons.AllIcons;
import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.ProjectScope;
import com.intellij.psi.search.scope.packageSet.FilteredPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public final class ProjectFilesScope extends NamedScope {
  public static final ProjectFilesScope INSTANCE = new ProjectFilesScope();

  public ProjectFilesScope() {
    super("Project Files", () -> ProjectScope.getProjectFilesScopeName(), AllIcons.Nodes.Folder, new FilteredPackageSet(ProjectScope.getProjectFilesScopeName()) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
        if (ScratchUtil.isScratch(file)) return true;
        ProjectFileIndex fileIndex = getFileIndex(project);
        return fileIndex != null && fileIndex.isInContent(file);
      }
    });
  }

  @Nullable
  static ProjectFileIndex getFileIndex(@NotNull Project project) {
    return !project.isInitialized() ? null : ProjectRootManager.getInstance(project).getFileIndex();
  }
}
