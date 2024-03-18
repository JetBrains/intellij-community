// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.*;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.daemon.impl.quickfix.CreateLocalVarFromInstanceofAction.*;
import static java.util.Objects.requireNonNull;

public final class CreateCastExpressionFromInstanceofAction implements ModCommandAction {
  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (!BaseIntentionAction.canModify(context.file())) return null;
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(context.file(), context.offset());
    if (instanceOfExpression == null) return null;
    PsiTypeElement checkType = instanceOfExpression.getCheckType();
    if (checkType == null || instanceOfExpression.getPattern() != null) return null;
    PsiExpression operand = instanceOfExpression.getOperand();
    PsiType operandType = operand.getType();
    if (TypeConversionUtil.isPrimitiveAndNotNull(operandType)) return null;
    PsiType type = checkType.getType();
    PsiStatement statement = PsiTreeUtil.getParentOfType(instanceOfExpression, PsiStatement.class);
    boolean insideIf = statement instanceof PsiIfStatement ifStatement
                       && PsiTreeUtil.isAncestor(ifStatement.getCondition(), instanceOfExpression, false);
    boolean insideWhile = statement instanceof PsiWhileStatement whileStatement
                          && PsiTreeUtil.isAncestor(whileStatement.getCondition(), instanceOfExpression, false);

    if (!insideIf && !insideWhile) return null;
    if (isAlreadyCastedTo(type, instanceOfExpression, statement)) return null;
    String castTo = type.getPresentableText();
    return Presentation.of(JavaBundle.message("cast.to.0", castTo));
  }

  @Override
  public @NotNull ModCommand perform(@NotNull ActionContext context) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(context.file(), context.offset());
    if (instanceOfExpression == null) return ModCommand.nop();
    return ModCommand.psiUpdate(instanceOfExpression, (expr, updater) -> invoke(context, expr, updater));
  }

  private static void invoke(@NotNull ActionContext context,
                             @NotNull PsiInstanceOfExpression instanceOfExpression,
                             @NotNull ModPsiUpdater updater) {
    PsiElement decl = createAndInsertCast(context.offset(), instanceOfExpression);
    if (decl == null) return;
    decl = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(decl);
    if (decl == null) return;
    decl = CodeStyleManager.getInstance(context.project()).reformat(decl);
    updater.moveCaretTo(decl.getTextRange().getEndOffset());
  }

  @Nullable
  private static PsiElement createAndInsertCast(int caretOffset, @NotNull PsiInstanceOfExpression instanceOfExpression) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(instanceOfExpression.getProject());
    PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText("((a)b)", instanceOfExpression);

    PsiParenthesizedExpression paren = (PsiParenthesizedExpression)statement.getExpression();
    PsiTypeCastExpression cast = (PsiTypeCastExpression)requireNonNull(paren.getExpression());
    PsiType castType = requireNonNull(instanceOfExpression.getCheckType()).getType();
    requireNonNull(cast.getCastType()).replace(factory.createTypeElement(castType));
    requireNonNull(cast.getOperand()).replace(instanceOfExpression.getOperand());

    final PsiStatement statementInside = isNegated(instanceOfExpression) ? null : 
                                         getExpressionStatementInside(caretOffset, instanceOfExpression.getOperand());
    if (statementInside != null) {
      return statementInside.replace(statement);
    }
    else {
      return insertAtAnchor(instanceOfExpression, statement);
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("cast.expression");
  }
}