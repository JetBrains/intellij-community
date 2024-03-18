// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public final class ConvertCompareToToEqualsIntention extends PsiUpdateModCommandAction<PsiBinaryExpression> {
  public ConvertCompareToToEqualsIntention() {
    super(PsiBinaryExpression.class);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiBinaryExpression element, @NotNull ModPsiUpdater updater) {
    final CompareToResult compareToResult = CompareToResult.findCompareTo(element);
    assert compareToResult != null;
    final PsiExpression qualifier = compareToResult.getQualifier();
    final PsiExpression argument = compareToResult.getArgument();
    final StringBuilder text = new StringBuilder();
    if (!compareToResult.isEqEq()) {
      text.append('!');
    }
    if (qualifier != null) {
      text.append(qualifier.getText()).append('.');
    }
    text.append("equals(").append(argument.getText()).append(')');
    final PsiExpression newExpression = JavaPsiFacade.getElementFactory(context.project()).createExpressionFromText(text.toString(), null);
    final PsiElement result = compareToResult.getBinaryExpression().replace(newExpression);
    updater.moveCaretTo(result.getTextOffset() + result.getTextLength());
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiBinaryExpression element) {
    if (CompareToResult.findCompareTo(element) == null) return null;
    return Presentation.of(JavaBundle.message("convert.compareto.expression.to.equals.call.may.change.semantics"));
  }

  private static final class CompareToResult {

    private final PsiBinaryExpression myBinaryExpression;
    private final PsiMethodCallExpression myCompareToCall;

    private CompareToResult(PsiBinaryExpression binaryExpression, PsiMethodCallExpression compareToCall) {
      myBinaryExpression = binaryExpression;
      myCompareToCall = compareToCall;
    }

    public PsiBinaryExpression getBinaryExpression() {
      return myBinaryExpression;
    }

    public boolean isEqEq() {
      return JavaTokenType.EQEQ.equals(myBinaryExpression.getOperationTokenType());
    }

    public PsiExpression getArgument() {
      return myCompareToCall.getArgumentList().getExpressions()[0];
    }

    public PsiExpression getQualifier() {
      return myCompareToCall.getMethodExpression().getQualifierExpression();
    }

    @Nullable
    static CompareToResult findCompareTo(PsiBinaryExpression binaryExpression) {
      if (binaryExpression == null) {
        return null;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (!JavaTokenType.NE.equals(tokenType) && !JavaTokenType.EQEQ.equals(tokenType)) {
        return null;
      }
      PsiMethodCallExpression compareToExpression;
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
      if (lhs instanceof PsiMethodCallExpression) {
        compareToExpression = (PsiMethodCallExpression)lhs;
        if (!MethodCallUtils.isCompareToCall(compareToExpression) || !ExpressionUtils.isZero(rhs)) {
          return null;
        }
      } else if (rhs instanceof PsiMethodCallExpression) {
        compareToExpression = (PsiMethodCallExpression)rhs;
        if (!ExpressionUtils.isZero(lhs) || !MethodCallUtils.isCompareToCall(compareToExpression)) {
          return null;
        }
      } else {
        return null;
      }
      return new CompareToResult(binaryExpression, compareToExpression);
    }
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("convert.compareto.expression.to.equals.call");
  }
}