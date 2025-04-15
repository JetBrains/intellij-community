// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public final class SwapIfStatementsIntentionAction extends PsiUpdateModCommandAction<PsiKeyword> {
  public SwapIfStatementsIntentionAction() {
    super(PsiKeyword.class);
  }
  
  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiKeyword keyword, @NotNull ModPsiUpdater updater) {
    final PsiIfStatement ifStatement = (PsiIfStatement)keyword.getParent();
    final PsiIfStatement nestedIfStatement = (PsiIfStatement) ifStatement.getElseBranch();
    assert nestedIfStatement != null;

    final PsiExpression condition = ifStatement.getCondition();
    final PsiExpression nestedCondition = nestedIfStatement.getCondition();

    final PsiStatement thenBranch = ifStatement.getThenBranch();
    final PsiStatement nestedThenBranch = nestedIfStatement.getThenBranch();

    assert condition != null;
    assert nestedCondition != null;
    assert thenBranch != null;
    assert nestedThenBranch != null;

    final PsiElement conditionCopy = condition.copy();
    condition.replace(nestedCondition);
    nestedCondition.replace(conditionCopy);

    final PsiElement thenBranchCopy = thenBranch.copy();
    thenBranch.replace(nestedThenBranch);
    nestedThenBranch.replace(thenBranchCopy);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiKeyword element) {
    if (!JavaKeywords.ELSE.equals(element.getText())) {
      return null;
    }
    final PsiElement parent = element.getParent();
    return isWellFormedIf(parent) && isWellFormedIf(((PsiIfStatement)parent).getElseBranch()) ? Presentation.of(getFamilyName()) : null;
  }
  
  private static boolean isWellFormedIf(@Nullable PsiElement e) {
    return e instanceof PsiIfStatement ifStatement && ifStatement.getCondition() != null && ifStatement.getThenBranch() != null;
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.swap.if.statements");
  }
}
