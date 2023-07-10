// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.ex.PerformFixesModalTask;
import com.intellij.lang.LangBundle;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModCommandExecutor.BatchExecutionResult;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;


public abstract class AbstractPerformFixesTask extends PerformFixesModalTask {
  protected final Class<?> myQuickfixClass;
  private final @NotNull Map<@NotNull BatchExecutionResult, Integer> myResultCount = new HashMap<>();

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
   * @param actionName user-readable name of the action
   * @return error message text; null if the action was successful
   */
  public final @Nullable @NlsContexts.Tooltip String getResultMessage(@NotNull String actionName) {
    if (myResultCount.isEmpty()) {
      return LangBundle.message("hint.text.unfortunately.currently.available.for.batch.mode", actionName);
    }
    if (myResultCount.size() == 1) {
      BatchExecutionResult result = myResultCount.keySet().iterator().next();
      if (result == ModCommandExecutor.Result.SUCCESS) return null;
      return LangBundle.message("executor.quick.fixes.cannot.be.executed") + "\n" + result.getMessage();
    }
    @Nls String message = LangBundle.message("executor.error.some.actions.failed") + "\n";
    int total = myResultCount.values().stream().mapToInt(i -> i).sum();
    return message + EntryStream.of(myResultCount) //NON-NLS
      .reverseSorted(Map.Entry.comparingByValue())
      .mapKeyValue((result, count) -> LangBundle.message("executor.one.of.actions", count, total, result.getMessage()))
      .joining("\n");
  }

  /**
   * @return whether the fix was applied
   */
  public final boolean isApplicableFixFound() {
    return !myResultCount.isEmpty();
  }
}
