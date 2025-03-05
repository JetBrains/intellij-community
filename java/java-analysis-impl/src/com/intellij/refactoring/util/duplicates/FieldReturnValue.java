// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FieldReturnValue implements ReturnValue {
  private final PsiField myField;

  public FieldReturnValue(PsiField psiField) {
    myField = psiField;
  }

  @Override
  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof FieldReturnValue)) return false;
    return myField == ((FieldReturnValue)other).myField;
  }

  public PsiField getField() {
    return myField;
  }

  @Override
  public @Nullable PsiStatement createReplacement(final @NotNull PsiMethod extractedMethod, final @NotNull PsiMethodCallExpression methodCallExpression, @Nullable PsiType returnType) throws IncorrectOperationException {

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(methodCallExpression.getProject());
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getProject());
    PsiExpressionStatement expressionStatement;
    expressionStatement = (PsiExpressionStatement)elementFactory.createStatementFromText("x = y();", null);
    expressionStatement = (PsiExpressionStatement)styleManager.reformat(expressionStatement);
    final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expressionStatement.getExpression();
    assignmentExpression.getLExpression().replace(elementFactory.createExpressionFromText(myField.getName(), myField));
    assignmentExpression.getRExpression().replace(methodCallExpression);
    return expressionStatement;

  }
}