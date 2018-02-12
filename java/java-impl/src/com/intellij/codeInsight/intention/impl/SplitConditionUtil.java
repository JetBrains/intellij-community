/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class SplitConditionUtil {
  public static PsiPolyadicExpression findCondition(PsiElement element) {
    return findCondition(element, true, true);
  }

  public static PsiPolyadicExpression findCondition(PsiElement element, boolean acceptAnd, boolean acceptOr) {
    if (!(element instanceof PsiJavaToken)) {
      return null;
    }
    PsiJavaToken token = (PsiJavaToken)element;
    if (!(token.getParent() instanceof PsiPolyadicExpression)) return null;

    PsiPolyadicExpression expression = (PsiPolyadicExpression)token.getParent();
    boolean isAndExpression = acceptAnd && expression.getOperationTokenType() == JavaTokenType.ANDAND;
    boolean isOrExpression = acceptOr && expression.getOperationTokenType() == JavaTokenType.OROR;
    if (!isAndExpression && !isOrExpression) return null;

    while (expression.getParent() instanceof PsiPolyadicExpression) {
      expression = (PsiPolyadicExpression)expression.getParent();
      if (isAndExpression && expression.getOperationTokenType() != JavaTokenType.ANDAND) return null;
      if (isOrExpression && expression.getOperationTokenType() != JavaTokenType.OROR) return null;
    }
    return expression;
  }

  public static PsiExpression getROperands(PsiPolyadicExpression expression, PsiJavaToken separator) throws IncorrectOperationException {
    PsiElement next = PsiTreeUtil.skipWhitespacesAndCommentsForward(separator);
    final int offsetInParent;
    if (next == null) {
      offsetInParent = separator.getStartOffsetInParent() + separator.getTextLength();
    } else {
      offsetInParent = next.getStartOffsetInParent();
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
    String rOperands = expression.getText().substring(offsetInParent);
    return factory.createExpressionFromText(rOperands, expression.getParent());
  }

  public static PsiExpression getLOperands(PsiPolyadicExpression expression, PsiJavaToken separator) throws IncorrectOperationException {
    PsiElement prev = separator;
    if (prev.getPrevSibling() instanceof PsiWhiteSpace) prev = prev.getPrevSibling();
    if (prev == null) {
      throw new IncorrectOperationException("Unable to split '"+expression.getText()+"' left to '"+separator+"' (offset "+separator.getStartOffsetInParent()+")");
    }

    PsiElementFactory factory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
    String rOperands = expression.getText().substring(0, prev.getStartOffsetInParent());
    return factory.createExpressionFromText(rOperands, expression.getParent());
  }
}
