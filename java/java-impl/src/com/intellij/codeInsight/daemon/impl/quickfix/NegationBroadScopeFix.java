// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * if (!a == b) ...  =>  if (!(a == b)) ...
 */
public class NegationBroadScopeFix extends PsiUpdateModCommandAction<PsiPrefixExpression> {
  public NegationBroadScopeFix(@NotNull PsiPrefixExpression prefixExpression) {
    super(prefixExpression);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiPrefixExpression expression) {
    PsiExpression operand = expression.getOperand();
    if (operand == null) return null;

    PsiElement parent = expression.getParent();
    String text = operand.getText() + " ";

    String rop;
    if (parent instanceof PsiInstanceOfExpression instanceOf) {
      if (instanceOf.getOperand() != expression) return null;
      text += JavaKeywords.INSTANCEOF + " ";
      final PsiTypeElement typeElement = instanceOf.getCheckType();
      rop = typeElement == null ? "" : typeElement.getText();
    }
    else if (parent instanceof PsiBinaryExpression binaryExpression) {
      if (binaryExpression.getLOperand() != expression) return null;
      if (!TypeConversionUtil.isBooleanType(binaryExpression.getType())) return null;
      text += binaryExpression.getOperationSign().getText() + " ";
      final PsiExpression rOperand = binaryExpression.getROperand();
      rop = rOperand == null ? "" : rOperand.getText();
    }
    else {
      return null;
    }

    text += rop;
    return Presentation.of(QuickFixBundle.message("negation.broader.scope.text", text));
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("negation.broader.scope.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiPrefixExpression myPrefixExpression, @NotNull ModPsiUpdater updater) {
    PsiExpression operand = Objects.requireNonNull(myPrefixExpression.getOperand());
    PsiElement unnegated = myPrefixExpression.replace(operand);
    PsiElement parent = unnegated.getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());

    PsiPrefixExpression negated = (PsiPrefixExpression)factory.createExpressionFromText("!(xxx)", parent);
    PsiParenthesizedExpression parentheses = (PsiParenthesizedExpression)Objects.requireNonNull(negated.getOperand());
    Objects.requireNonNull(parentheses.getExpression()).replace(parent.copy());
    parent.replace(negated);
  }
}
