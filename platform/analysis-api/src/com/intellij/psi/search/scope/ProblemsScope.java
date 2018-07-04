// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.scope;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.search.scope.packageSet.CustomScopesProvider;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.FilteredPackageSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Sergey Malenkov
 */
public final class ProblemsScope extends NamedScope {
  public static final String NAME = IdeBundle.message("predefined.scope.problems.name");
  public static final ProblemsScope INSTANCE = new ProblemsScope();

  private ProblemsScope() {
    super(NAME, AllIcons.Scope.Problems, new FilteredPackageSet(NAME) {
      @Override
      public boolean contains(@NotNull VirtualFile file, @NotNull Project project) {
        WolfTheProblemSolver solver = project.isDisposed() ? null : WolfTheProblemSolver.getInstance(project);
        return solver != null && solver.isProblemFile(file);
      }
    });
  }

  public static final class Provider implements CustomScopesProvider {
    @NotNull
    @Override
    public List<NamedScope> getCustomScopes() {
      return Collections.singletonList(INSTANCE);
    }
  }
}
