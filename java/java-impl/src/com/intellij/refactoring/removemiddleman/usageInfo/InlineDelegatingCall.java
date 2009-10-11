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
package com.intellij.refactoring.removemiddleman.usageInfo;

import com.intellij.psi.*;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class InlineDelegatingCall extends FixableUsageInfo {
  private final PsiMethodCallExpression expression;
  private final String myAccess;
  private final String delegatingName;
  private final int[] paramaterPermutation;

  public InlineDelegatingCall(PsiMethodCallExpression expression,
                              int[] paramaterPermutation,
                              String access,
                              String delegatingName) {
    super(expression);
    this.expression = expression;
    this.paramaterPermutation = paramaterPermutation;
    myAccess = access;
    this.delegatingName = delegatingName;
  }

  public void fixUsage() throws IncorrectOperationException {
    final StringBuffer replacementText = new StringBuffer();
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiElement qualifier = methodExpression.getQualifier();
    if (qualifier != null) {
      final String qualifierText = qualifier.getText();
      replacementText.append(qualifierText + '.');
    }
    replacementText.append(myAccess).append(".");
    replacementText.append(delegatingName).append('(');
    final PsiExpressionList argumentList = expression.getArgumentList();
    assert argumentList != null;
    final PsiExpression[] args = argumentList.getExpressions();
    boolean first = true;
    for (int i : paramaterPermutation) {
      if (!first) {
        replacementText.append(", ");
      }
      first = false;
      final String argText = args[i].getText();
      replacementText.append(argText);
    }
    replacementText.append(')');
    final String replacementTextString = replacementText.toString();
    MutationUtils.replaceExpression(replacementTextString, expression);
  }
}
