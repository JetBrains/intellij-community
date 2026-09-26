// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiTarget;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
@ApiStatus.Internal
public final class PomDefinitionSearch implements QueryExecutor<PsiElement, PsiElement> {
  @Override
  public boolean execute(@NotNull PsiElement queryParameters, @NotNull Processor<? super PsiElement> consumer) {
    if (queryParameters instanceof PomTargetPsiElement) {
      final PomTarget target = ((PomTargetPsiElement)queryParameters).getTarget();
      if (target instanceof PsiTarget) {
        if (!consumer.process(((PsiTarget)target).getNavigationElement())) return false;
      }
    }
    return true;
  }
}
