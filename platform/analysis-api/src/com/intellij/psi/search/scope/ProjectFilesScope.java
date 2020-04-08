// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
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
    super(getNameText(), AllIcons.Nodes.Folder, new FilteredPackageSet(getNameText()) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
        ProjectFileIndex fileIndex = getFileIndex(project);
        return fileIndex != null
               && !fileIndex.isExcluded(file)
               && fileIndex.getContentRootForFile(file) != null;
      }
    });
  }

  @Nullable
  static ProjectFileIndex getFileIndex(@NotNull Project project) {
    return !project.isInitialized() ? null : ProjectRootManager.getInstance(project).getFileIndex();
  }

  public static String getNameText() {
    return AnalysisBundle.message("predefined.scope.project.files.name");
  }
}
