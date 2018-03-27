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

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.ExpressionUtils;

/**
 * @author ven
 */
public class ConditionalReturnStatementValue implements ReturnValue {
  PsiExpression myReturnValue;

  public ConditionalReturnStatementValue(final PsiExpression returnValue) {
    myReturnValue = returnValue;
  }

  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof ConditionalReturnStatementValue)) return false;
    PsiExpression otherReturnValue = ((ConditionalReturnStatementValue) other).myReturnValue;
    if (otherReturnValue == null || myReturnValue == null) return myReturnValue == null && otherReturnValue == null;
    return PsiEquivalenceUtil.areElementsEquivalent(myReturnValue, otherReturnValue);
  }

  public PsiStatement createReplacement(final PsiMethod extractedMethod, PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
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
