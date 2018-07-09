/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExtractIfConditionAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null || ifStatement.getCondition() == null) {
      return false;
    }

    final PsiExpression condition = ifStatement.getCondition();

    if (!(condition instanceof PsiPolyadicExpression)) {
      return false;
    }

    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;
    final PsiType expressionType = polyadicExpression.getType();
    if (expressionType == null || !PsiType.BOOLEAN.isAssignableFrom(expressionType)) {
      return false;
    }

    final IElementType operation = polyadicExpression.getOperationTokenType();

    if (operation != JavaTokenType.OROR && operation != JavaTokenType.ANDAND) {
      return false;
    }

    final PsiExpression operand = findOperand(element, polyadicExpression);

    if (operand == null) {
      return false;
    }
    setText(CodeInsightBundle.message("intention.extract.if.condition.text", PsiExpressionTrimRenderer.render(operand)));
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class);
    if (ifStatement == null) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

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

    final PsiExpression condition = ifStatement.getCondition();

    if (!(condition instanceof PsiPolyadicExpression)) {
      return null;
    }

    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;

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
      if (token != null && sb.length() != 0) {
        sb.append(token.getText()).append(" ");
      }
      sb.append(tracker.text(e));
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
    return CodeInsightBundle.message("intention.extract.if.condition.family");
  }
}
