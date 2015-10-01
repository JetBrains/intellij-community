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

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;

public class ReplaceStaticVariableAccess extends FixableUsageInfo {
  private final PsiReferenceExpression expression;
  private final String delegateClass;
  private final boolean myEnumConstant;
  private static final Logger LOGGER = Logger.getInstance("#" + ReplaceStaticVariableAccess.class.getName());

  public ReplaceStaticVariableAccess(PsiReferenceExpression expression, String delegateClass, boolean enumConstant) {
    super(expression);
    this.expression = expression;
    this.delegateClass = delegateClass;
    myEnumConstant = enumConstant;
  }

  public void fixUsage() throws IncorrectOperationException {
    if (myEnumConstant) {
      final PsiSwitchLabelStatement switchStatement = PsiTreeUtil.getParentOfType(expression, PsiSwitchLabelStatement.class);
      if (switchStatement != null) {
        MutationUtils.replaceExpression(expression.getReferenceName(), expression);
        return;
      }
    }
    final boolean replaceWithGetEnumValue = myEnumConstant && !alreadyMigratedToEnum();
    final String link = replaceWithGetEnumValue ? "." + GenerateMembersUtil.suggestGetterName("value", expression.getType(), expression.getProject()) + "()" : "";
    MutationUtils.replaceExpression(delegateClass + '.' + expression.getReferenceName() + link, expression);
  }

  private boolean alreadyMigratedToEnum() {
    final PsiCallExpression callExpression = PsiTreeUtil.getParentOfType(expression, PsiCallExpression.class);
    if (callExpression != null) {
      final PsiMethod resolvedMethod = callExpression.resolveMethod();
      if (resolvedMethod != null) {
        final PsiParameter[] parameters = resolvedMethod.getParameterList().getParameters();
        final PsiExpression[] args = callExpression.getArgumentList().getExpressions();
        int idx = -1;
        for (int i = 0; i < args.length; i++) {
          if (PsiTreeUtil.isAncestor(args[i], expression, false)) {
            idx = i;
            break;
          }
        }
        if (idx != -1 && parameters[idx].getType().equalsToText(delegateClass)) {
          return true;
        }
      }
    }
    else {
      final PsiReturnStatement returnStatement = PsiTreeUtil.getParentOfType(expression, PsiReturnStatement.class);
      if (returnStatement != null) {
        final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        LOGGER.assertTrue(psiMethod != null);
        final PsiType returnType = psiMethod.getReturnType();
        if (returnType != null && returnType.getCanonicalText().equals(delegateClass)) {
          return true;
        }
      } else {
        final PsiVariable psiVariable = PsiTreeUtil.getParentOfType(expression, PsiVariable.class);
        if (psiVariable != null) {
          if (psiVariable.getType().equalsToText(delegateClass)) {
            return true;
          }
        } else {
          final PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(expression, PsiAssignmentExpression.class);
          if (assignmentExpression != null && assignmentExpression.getRExpression() == expression) {
            final PsiExpression lExpression = assignmentExpression.getLExpression();
            if (lExpression instanceof PsiReferenceExpression) {
              final PsiElement resolve = ((PsiReferenceExpression)lExpression).resolve();
              if (resolve instanceof PsiVariable && ((PsiVariable)resolve).getType().equalsToText(delegateClass)) {
                return true;
              }
            }
          }
          else {
            final PsiBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(expression, PsiBinaryExpression.class);
            if (binaryExpression != null && binaryExpression.getOperationTokenType() == JavaTokenType.EQEQ) {
              final PsiExpression[] operands = binaryExpression.getOperands();
              final int index = ArrayUtil.find(operands, expression);
              if (index >= 0) {
                final PsiType type = operands[index].getType();
                return type != null && type.equalsToText(delegateClass);
              }
            }
          }
        }
      }
    }
    return false;
  }
}
