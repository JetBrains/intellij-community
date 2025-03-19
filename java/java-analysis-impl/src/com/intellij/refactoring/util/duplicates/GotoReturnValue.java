// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class GotoReturnValue implements ReturnValue {
  @Override
  public @Nullable PsiStatement createReplacement(final @NotNull PsiMethod extractedMethod, final @NotNull PsiMethodCallExpression methodCallExpression, @Nullable PsiType returnType) throws IncorrectOperationException {
    if (!TypeConversionUtil.isBooleanType(extractedMethod.getReturnType())) return null;
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(methodCallExpression.getProject());
    final PsiIfStatement statement = (PsiIfStatement)elementFactory.createStatementFromText(getGotoStatement(), null);
    final PsiExpression condition = statement.getCondition();
    assert condition != null;
    condition.replace(methodCallExpression);
    return (PsiStatement)CodeStyleManager.getInstance(statement.getManager().getProject()).reformat(statement);
  }

  public abstract @NonNls String getGotoStatement();
}