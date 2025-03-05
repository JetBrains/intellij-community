// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.duplicates;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConditionalReturnStatementValue implements ReturnValue {
  PsiExpression myReturnValue;

  public ConditionalReturnStatementValue(final PsiExpression returnValue) {
    myReturnValue = returnValue;
  }

  @Override
  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof ConditionalReturnStatementValue)) return false;
    PsiExpression otherReturnValue = ((ConditionalReturnStatementValue) other).myReturnValue;
    if (otherReturnValue == null || myReturnValue == null) return myReturnValue == null && otherReturnValue == null;
    return PsiEquivalenceUtil.areElementsEquivalent(myReturnValue, otherReturnValue);
  }

  @Override
  public @Nullable PsiStatement createReplacement(final @NotNull PsiMethod extractedMethod, @NotNull PsiMethodCallExpression methodCallExpression, @Nullable PsiType returnType) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(methodCallExpression.getProject());
    PsiIfStatement statement;
    if (myReturnValue == null) {
      statement = (PsiIfStatement)elementFactory.createStatementFromText("if(a) return;", null);
    }
    else {
      statement = (PsiIfStatement)elementFactory.createStatementFromText("if(a) return b;", null);
      final PsiReturnStatement thenBranch = (PsiReturnStatement)statement.getThenBranch();
      assert thenBranch != null;
      final PsiExpression returnValue = thenBranch.getReturnValue();
      assert returnValue != null;
      returnValue.replace(myReturnValue);
    }

    final PsiExpression condition = statement.getCondition();
    assert condition != null;
    condition.replace(methodCallExpression);
    return (PsiStatement)CodeStyleManager.getInstance(statement.getManager().getProject()).reformat(statement);
  }

  public boolean isEmptyOrConstantExpression() {
    return myReturnValue == null || ExpressionUtils.isNullLiteral(myReturnValue) || PsiUtil.isConstantExpression(myReturnValue);
  }
}
