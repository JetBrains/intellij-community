// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.FilteredPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class ProjectProductionScope extends NamedScope {
  public static final ProjectProductionScope INSTANCE = new ProjectProductionScope();

  private ProjectProductionScope() {
    super("Production", () ->getNameText(), IconManager.getInstance().createOffsetIcon(AllIcons.Scope.Production), new FilteredPackageSet(getNameText()) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
        ProjectFileIndex index = ProjectFilesScope.getFileIndex(project);
        return index != null
               && index.isInSource(file)
               && !index.isInLibrary(file)
               && !TestSourcesFilter.isTestSources(file, project);
      }
    });
  }

  public static String getNameText() {
    return AnalysisBundle.message("predefined.scope.production.name");
  }
}
