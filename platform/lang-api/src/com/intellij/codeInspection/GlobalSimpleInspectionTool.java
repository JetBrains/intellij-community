/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.analysis.AnalysisScope;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Global inspection tool which doesn't need the graph and, therefore, can be run on per-file basis concurrently.
 * Basically it is a local inspection tool which cannot be selected in the inspection profile to be run on-the-fly.
 */
public abstract class GlobalSimpleInspectionTool extends GlobalInspectionTool {
  public void inspectionStarted(@NotNull InspectionManager manager,
                                @NotNull GlobalInspectionContext globalContext,
                                @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {}
  public void inspectionFinished(@NotNull InspectionManager manager,
                                 @NotNull GlobalInspectionContext globalContext,
                                 @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {}
  public abstract void checkFile(@NotNull PsiFile file,
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
  public final boolean isGraphNeeded() {
    return false;
  }
}
