// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.duplicates;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.Callable;

final class MethodDuplicatesServiceImpl implements MethodDuplicatesService {

  @Override
  public void replaceMethodDuplicates(@NotNull PsiMethod method) {
    Project project = method.getProject();
    final Callable<@Nullable MatchProvider> runnable = () -> {
      if (!method.isValid()) return null;
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return null;

      final List<Match> duplicates = MethodDuplicatesHandler.hasDuplicates(containingClass, method);
      duplicates.removeIf(match -> PsiTreeUtil.isAncestor(method, match.getMatchStart(), false));
      return duplicates.isEmpty() ? null : MatchProvider.create(method, duplicates);
    };
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      () -> ReadAction.nonBlocking(runnable)
        .finishOnUiThread(ModalityState.nonModal(), matchProvider -> {
          MethodDuplicatesHandler.replaceDuplicate(project, ContainerUtil.createMaybeSingletonList(matchProvider));
        })
        .expireWhen(() -> !method.isValid())
        .submit(AppExecutorUtil.getAppExecutorService()),
      JavaRefactoringBundle.message("replace.method.code.duplicates.title"), true, project);
  }
}
