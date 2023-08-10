
// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.guess;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class GuessManager {
  public static GuessManager getInstance(Project project) {
    return project.getService(GuessManager.class);
  }

  public abstract PsiType @NotNull [] guessContainerElementType(PsiExpression containerExpr, TextRange rangeToIgnore);

  public abstract PsiType @NotNull [] guessTypeToCast(PsiExpression expr);

  @NotNull
  public abstract MultiMap<PsiExpression, PsiType> getControlFlowExpressionTypes(@NotNull PsiExpression forPlace, boolean honorAssignments);

  @NotNull
  public List<PsiType> getControlFlowExpressionTypeConjuncts(@NotNull PsiExpression expr) {
    return getControlFlowExpressionTypeConjuncts(expr, true);
  }

  @NotNull
  public abstract List<PsiType> getControlFlowExpressionTypeConjuncts(@NotNull PsiExpression expr, boolean honorAssignments);
}