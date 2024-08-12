// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.packageDependencies.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.project.Project;
import com.intellij.packageDependencies.BackwardDependenciesBuilder;
import com.intellij.packageDependencies.DependenciesBuilder;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BackwardDependenciesHandler extends DependenciesHandlerBase {
  private final AnalysisScope myScopeOfInterest;

  public BackwardDependenciesHandler(@NotNull Project project, AnalysisScope scope, final AnalysisScope selectedScope) {
    this(project, Collections.singletonList(scope), selectedScope, new HashSet<>());
  }

  public BackwardDependenciesHandler(@NotNull Project project, final List<? extends AnalysisScope> scopes, final @Nullable AnalysisScope scopeOfInterest, Set<PsiFile> excluded) {
    super(project, scopes, excluded);
    myScopeOfInterest = scopeOfInterest;
  }

  @Override
  protected String getProgressTitle() {
    return CodeInsightBundle.message("backward.dependencies.progress.text");
  }

  @Override
  protected String getPanelDisplayName(final AnalysisScope scope) {
    return CodeInsightBundle.message("backward.dependencies.toolwindow.title", scope.getDisplayName());
  }

  @Override
  protected DependenciesBuilder createDependenciesBuilder(AnalysisScope scope) {
    return new BackwardDependenciesBuilder(myProject, scope, myScopeOfInterest);
  }
}
