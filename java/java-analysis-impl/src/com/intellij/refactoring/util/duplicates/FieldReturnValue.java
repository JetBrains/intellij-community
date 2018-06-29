/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dsl
 */
public class FieldReturnValue implements ReturnValue {
  private final PsiField myField;

  public FieldReturnValue(PsiField psiField) {
    myField = psiField;
  }

  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof FieldReturnValue)) return false;
    return myField == ((FieldReturnValue)other).myField;
  }

  public PsiField getField() {
    return myField;
  }

  @Nullable
  public PsiStatement createReplacement(@NotNull final PsiMethod extractedMethod, @NotNull final PsiMethodCallExpression methodCallExpression, @Nullable PsiType returnType) throws IncorrectOperationException {

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
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