// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.search.scope.packageSet.FilteredPackageSet;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import org.jetbrains.annotations.NotNull;

/**
 * @see com.intellij.codeInsight.problems.WolfTheProblemSolverImpl#processProblemFiles
 * @deprecated this scope is obsolete and will be removed soon
 */
@Deprecated(forRemoval = true)
public final class ProblemsScope extends NamedScope {
  public static final ProblemsScope INSTANCE = new ProblemsScope();

  private ProblemsScope() {
    super("Problems", () -> getNameText(), AllIcons.Scope.Problems, new FilteredPackageSet(getNameText()) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
        WolfTheProblemSolver solver = project.isDisposed() ? null : WolfTheProblemSolver.getInstance(project);
        return solver != null && solver.isProblemFile(file);
      }
    });
  }

  public static String getNameText() {
    return AnalysisBundle.message("predefined.scope.problems.name");
  }
}
