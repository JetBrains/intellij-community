// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dsl
 */
public final class ReturnStatementReturnValue implements ReturnValue {
  public static final ReturnStatementReturnValue INSTANCE = new ReturnStatementReturnValue();

  private ReturnStatementReturnValue() {}

  @Override
  public boolean isEquivalent(ReturnValue other) {
    return other instanceof ReturnStatementReturnValue;
  }

  @Override
  @Nullable
  public PsiStatement createReplacement(@NotNull final PsiMethod extractedMethod, @NotNull final PsiMethodCallExpression methodCallExpression,@Nullable PsiType returnType) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(methodCallExpression.getProject());
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getProject());
    PsiReturnStatement returnStatement = (PsiReturnStatement)elementFactory.createStatementFromText("return x;", null);
    returnStatement = (PsiReturnStatement) styleManager.reformat(returnStatement);
    returnStatement.getReturnValue().replace(methodCallExpression);
    return returnStatement;
  }
}
