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
package com.intellij.refactoring.introduceparameterobject.usageInfo;

import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceParameterAssignmentWithCall extends FixableUsageInfo {
  private final PsiReferenceExpression expression;
  private final String newParameterName;
  private final String setterName;
  private final String getterName;

  public ReplaceParameterAssignmentWithCall(PsiReferenceExpression element, String newParameterName, String setterName, String getterName) {
    super(element);
    this.setterName = setterName;
    this.getterName = getterName;
    this.newParameterName = newParameterName;
    expression = element;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    final PsiAssignmentExpression assignment = PsiTreeUtil.getParentOfType(expression, PsiAssignmentExpression.class);
    assert assignment != null;
    final PsiExpression rhs = assignment.getRExpression();
    if (rhs == null) {
      return;
    }
    final String rhsText = rhs.getText();
    final String operator = assignment.getOperationSign().getText();
    final String newExpression;
    if ("=".equals(operator)) {
      newExpression = newParameterName + '.' + setterName + '(' + rhsText + ')';
    }
    else {
      final String strippedOperator = operator.substring(0, operator.length() - 1);
      newExpression =
        newParameterName + '.' + setterName + '(' + newParameterName + '.' + getterName + "()" + strippedOperator + rhsText + ')';
    }
    MutationUtils.replaceExpression(newExpression, assignment);
  }

}
