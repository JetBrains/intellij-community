// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Global inspection tool which doesn't need the reference graph and, therefore, can be run on per-file basis concurrently.
 * Basically it is a local inspection tool that cannot be selected in the inspection profile to be run on-the-fly.
 */
public abstract class GlobalSimpleInspectionTool extends GlobalInspectionTool {
  public void inspectionStarted(@NotNull InspectionManager manager,
                                @NotNull GlobalInspectionContext globalContext,
                                @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {}

  public void inspectionFinished(@NotNull InspectionManager manager,
                                 @NotNull GlobalInspectionContext globalContext,
                                 @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {}

  @Override
  public abstract void checkFile(@NotNull PsiFile psiFile,
                                 @NotNull InspectionManager manager,
                                 @NotNull ProblemsHolder problemsHolder,
                                 @NotNull GlobalInspectionContext globalContext,
                                 @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor);

  @Override
  public final void runInspection(@NotNull AnalysisScope scope,
                                  @NotNull InspectionManager manager,
                                  @NotNull GlobalInspectionContext globalContext,
                                  @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    throw new IncorrectOperationException("You must override checkFile() instead");
  }

  @Override
  public boolean isReadActionNeeded() {
    return false;
  }

  @Override
  public final boolean isGraphNeeded() {
    return false;
  }

  @Override
  public final boolean isGlobalSimpleInspectionTool() {
    return true;
  }
}
