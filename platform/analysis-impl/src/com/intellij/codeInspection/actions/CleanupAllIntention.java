// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class CleanupAllIntention extends CleanupIntention {

  public static final CleanupAllIntention INSTANCE = new CleanupAllIntention();

  private CleanupAllIntention() {}

  @Override
  public @NotNull String getFamilyName() {
    return AnalysisBundle.message("cleanup.in.file");
  }

  @Override
  protected @NotNull AnalysisScope getScope(Project project, PsiFile psiFile) {
    return new AnalysisScope(psiFile);
  }
}
