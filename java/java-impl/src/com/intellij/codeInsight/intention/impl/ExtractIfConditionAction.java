// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExtractIfConditionAction extends PsiUpdateModCommandAction<PsiElement> {
  public ExtractIfConditionAction() {
    super(PsiElement.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null) return null;

    final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());
    if (!(condition instanceof PsiPolyadicExpression polyadicExpression)) return null;

    final PsiType expressionType = polyadicExpression.getType();
    if (expressionType == null || !PsiTypes.booleanType().isAssignableFrom(expressionType)) return null;

    final IElementType operation = polyadicExpression.getOperationTokenType();
    if (operation != JavaTokenType.OROR && operation != JavaTokenType.ANDAND) return null;

    final PsiExpression operand = findOperand(element, polyadicExpression);
    if (operand == null) return null;
    return Presentation.of(JavaBundle.message("intention.extract.if.condition.text", PsiExpressionTrimRenderer.render(operand)));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(context.project());

    CommentTracker tracker = new CommentTracker();
    final PsiStatement newIfStatement = create(factory, ifStatement, element, tracker);
    if (newIfStatement == null) {
      return;
    }

    codeStyleManager.reformat(tracker.replaceAndRestoreComments(ifStatement, newIfStatement));
  }

  @Nullable
  private static PsiStatement create(@NotNull PsiElementFactory factory,
                                     @NotNull PsiIfStatement ifStatement,
                                     @NotNull PsiElement element,
                                     CommentTracker tracker) {

    final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(ifStatement.getCondition());

    if (!(condition instanceof PsiPolyadicExpression polyadicExpression)) {
      return null;
    }

    final PsiExpression operand = findOperand(element, polyadicExpression);

    if (operand == null) {
      return null;
    }

    PsiExpression leave = removeOperand(factory, polyadicExpression, operand, tracker);
    return SplitConditionUtil.create(factory, ifStatement, operand, leave, polyadicExpression.getOperationTokenType(), tracker);
  }

  @NotNull
  private static PsiExpression removeOperand(@NotNull PsiElementFactory factory,
                                             @NotNull PsiPolyadicExpression expression,
                                             @NotNull PsiExpression operand,
                                             CommentTracker tracker) {
    final StringBuilder sb = new StringBuilder();
    for (PsiExpression e : expression.getOperands()) {
      if (e == operand) continue;
      final PsiJavaToken token = expression.getTokenBeforeOperand(e);
      if (token != null && !sb.isEmpty()) {
        sb.append(token.getText()).append(" ");
      }
      tracker.markUnchanged(ObjectUtils.notNull(PsiUtil.skipParenthesizedExprDown(e), e));
      sb.append(e.getText());
    }
    return factory.createExpressionFromText(sb.toString(), expression);
  }

  @Nullable
  private static PsiExpression findOperand(@NotNull PsiElement e, @NotNull PsiPolyadicExpression expression) {
    final TextRange elementTextRange = e.getTextRange();

    for (PsiExpression operand : expression.getOperands()) {
      final TextRange operandTextRange = operand.getTextRange();
      if (operandTextRange != null && operandTextRange.contains(elementTextRange)) {
        return operand;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.extract.if.condition.family");
  }
}
