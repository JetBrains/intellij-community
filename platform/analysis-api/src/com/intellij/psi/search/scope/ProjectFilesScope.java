/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.psi.search.scope;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.AbstractPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public class ProjectFilesScope extends NamedScope {
  public static final String NAME = "Project Files";
  public ProjectFilesScope() {
    super(NAME, new AbstractPackageSet("ProjectFiles") {
      @Override
      public boolean contains(VirtualFile file, NamedScopesHolder holder) {
        return contains(file, holder.getProject(), holder);
      }

      @Override
      public boolean contains(VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
        if (file == null) return false;
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        return project.isInitialized()
               && !fileIndex.isExcluded(file)
               && fileIndex.getContentRootForFile(file) != null;
      }
    });
  }
}
