// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class CleanupAllIntention extends CleanupIntention {

  public static final CleanupAllIntention INSTANCE = new CleanupAllIntention();

  private CleanupAllIntention() {}

  @NotNull
  @Override
  public String getFamilyName() {
    return AnalysisBundle.message("cleanup.in.file");
  }

  @Nullable
  @Override
  protected AnalysisScope getScope(Project project, PsiFile file) {
    return new AnalysisScope(file);
  }
}
