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
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceInstanceVariableAssignment extends FixableUsageInfo {
    private final String setterName;
    private final PsiAssignmentExpression assignment;
    private final String getterName;
    private final String delegateName;

    public ReplaceInstanceVariableAssignment(PsiAssignmentExpression assignment,
                                      String delegateName,
                                      String setterName,
                                      String getterName) {
        super(assignment);
        this.assignment = assignment;
        this.getterName = getterName;
        this.setterName = setterName;
        this.delegateName = delegateName;
    }

    public void fixUsage() throws IncorrectOperationException {
        final PsiReferenceExpression lhs =
                (PsiReferenceExpression) assignment.getLExpression();
        final PsiExpression rhs = assignment.getRExpression();
        assert rhs != null;
        final PsiElement qualifier = lhs.getQualifier();
        final PsiJavaToken sign = assignment.getOperationSign();
        final String operator = sign.getText();
        final String rhsText = rhs.getText();
        final String newExpression;
        if (qualifier != null) {
            final String qualifierText = qualifier.getText();
            if ("=".equals(operator)) {
                newExpression = qualifierText + '.' + delegateName + '.' + setterName + "( " + rhsText + ')';
            } else {
                final String strippedOperator = getStrippedOperator(operator);
                newExpression = qualifierText + '.'+delegateName + '.' + setterName + '(' + qualifierText + '.'+delegateName + '.' + getterName + "()" + strippedOperator + rhsText + ')';
            }
        } else {
            if ("=".equals(operator)) {
                newExpression = delegateName + '.' + setterName + "( " + rhsText + ')';
            } else {
                final String strippedOperator = getStrippedOperator(operator);
                newExpression = delegateName + '.' + setterName + '(' + delegateName + '.' + getterName + "()" + strippedOperator + rhsText + ')';
            }
        }
        MutationUtils.replaceExpression(newExpression, assignment);
    }

    private static String getStrippedOperator(String operator) {
        return operator.substring(0, operator.length() - 1);
    }
}
