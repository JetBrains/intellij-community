// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.intention.impl.SplitConditionUtil.getLOperands;
import static com.intellij.codeInsight.intention.impl.SplitConditionUtil.getROperands;

public final class SplitIfAction extends PsiUpdateModCommandAction<PsiJavaToken> {
  public SplitIfAction() {
    super(PsiJavaToken.class);
  }
  
  private static final Logger LOG = Logger.getInstance(SplitIfAction.class);

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaToken element) {
    final PsiPolyadicExpression expression = SplitConditionUtil.findCondition(element);
    if (expression == null) return null;

    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (!(parent instanceof PsiIfStatement ifStatement)) return null;

    if (!PsiTreeUtil.isAncestor(ifStatement.getCondition(), expression, false)) return null;
    if (ifStatement.getThenBranch() == null) return null;
    return Presentation.of(JavaBundle.message("intention.split.if.text"));
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.split.if.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiJavaToken token, @NotNull ModPsiUpdater updater) {
    LOG.assertTrue(token.getTokenType() == JavaTokenType.ANDAND || token.getTokenType() == JavaTokenType.OROR);

    PsiPolyadicExpression expression = (PsiPolyadicExpression)token.getParent();
    PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(expression, PsiIfStatement.class);
    if (ifStatement == null) return;

    PsiExpression condition = ifStatement.getCondition();
    LOG.assertTrue(PsiTreeUtil.isAncestor(condition, expression, false));

    CommentTracker ct = new CommentTracker();
    PsiExpression lOperand = getLOperands(expression, token, ct);
    PsiExpression rOperand = getROperands(expression, token, ct);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(expression.getProject());

    PsiIfStatement replacement =
      SplitConditionUtil.create(factory, ifStatement, lOperand, rOperand, token.getTokenType(), ct);
    if (replacement == null) return;
    PsiElement result = ct.replaceAndRestoreComments(ifStatement, replacement);
    result = CodeStyleManager.getInstance(context.project()).reformat(result);
    if (result instanceof PsiIfStatement resultingIf) {
      PsiExpression resultCondition = resultingIf.getCondition();
      if (resultCondition != null) {
        updater.moveTo(resultCondition);
      }
    }
  }
}
