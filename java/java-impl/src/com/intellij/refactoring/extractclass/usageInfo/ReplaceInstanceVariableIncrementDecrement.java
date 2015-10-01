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
package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class ReplaceInstanceVariableIncrementDecrement extends FixableUsageInfo {
  private final PsiExpression reference;
  private final @Nullable String setterName;
  private final @Nullable String getterName;
  private final String delegateName;
  private final String fieldName;

  public ReplaceInstanceVariableIncrementDecrement(PsiExpression reference,
                                                   String delegateName,
                                                   String setterName,
                                                   String getterName,
                                                   String name) {
    super(reference);
    this.getterName = getterName;
    this.setterName = setterName;
    this.delegateName = delegateName;
    fieldName = name;
    final PsiPrefixExpression prefixExpr = PsiTreeUtil.getParentOfType(reference, PsiPrefixExpression.class);
    if (prefixExpr != null) {
      this.reference = prefixExpr;
    }
    else {
      this.reference = PsiTreeUtil.getParentOfType(reference, PsiPostfixExpression.class);
    }
  }

  public void fixUsage() throws IncorrectOperationException {

    final PsiReferenceExpression lhs;
    final PsiJavaToken sign;
    if (reference instanceof PsiPrefixExpression) {
      lhs = (PsiReferenceExpression)((PsiPrefixExpression)reference).getOperand();
      sign = ((PsiPrefixExpression)reference).getOperationSign();
    }
    else {
      lhs = (PsiReferenceExpression)((PsiPostfixExpression)reference).getOperand();
      sign = ((PsiPostfixExpression)reference).getOperationSign();
    }
    final PsiElement qualifier = lhs.getQualifier();
    final String operator = sign.getText();
    final String newExpression;
    if (getterName == null && setterName == null) {
      newExpression = (qualifier != null ? qualifier.getText() + "." : "") + delegateName + "." + fieldName + operator;
    } else {
      final String strippedOperator = getStrippedOperator(operator);
      newExpression = (qualifier != null ? qualifier.getText() + "." : "") + delegateName + 
                      '.' + callSetter(delegateName + '.' + callGetter() + strippedOperator + "1");
    }
    MutationUtils.replaceExpression(newExpression, reference);
  }

  private String callGetter() {
    return (getterName != null ? getterName + "()" : fieldName);
  }

  private String callSetter(String rhsText) {
    return setterName != null ? setterName + "(" + rhsText + ")" : fieldName + "=" + rhsText;
  }

  private static String getStrippedOperator(String operator) {
    return operator.substring(0, operator.length() - 1);
  }
}
