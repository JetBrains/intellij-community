// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.PerformFixesModalTask;
import com.intellij.modcommand.ModCommandExecutor.BatchExecutionResult;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class AbstractPerformFixesTask extends PerformFixesModalTask {
  protected final Class<?> myQuickfixClass;

  public AbstractPerformFixesTask(@NotNull Project project,
                                  CommonProblemDescriptor @NotNull [] descriptors,
                                  @Nullable Class<?> quickfixClass) {
    super(project, descriptors);
    myQuickfixClass = quickfixClass;
  }

  protected abstract <D extends CommonProblemDescriptor> BatchExecutionResult collectFix(QuickFix<D> fix, D descriptor, Project project);

  @Override
  protected final void applyFix(Project project, CommonProblemDescriptor descriptor) {
    //noinspection unchecked
    QuickFix<ProblemDescriptor>[] fixes = descriptor.getFixes();
    if (fixes != null) {
      for (final QuickFix<ProblemDescriptor> fix : fixes) {
        if (fix != null && (myQuickfixClass == null || fix.getClass().isAssignableFrom(myQuickfixClass))) {
          final ProblemDescriptor problemDescriptor = (ProblemDescriptor)descriptor;
          final PsiElement element = problemDescriptor.getPsiElement();
          if (element != null && element.isValid()) {
            BatchExecutionResult result = collectFix(fix, problemDescriptor, project);
            myResultCount.merge(result, 1, Integer::sum);
          }
          break;
        }
      }
    }
  }

  /**
   * @return whether the fix was applied
   */
  public final boolean isApplicableFixFound() {
    return !myResultCount.isEmpty();
  }
}
