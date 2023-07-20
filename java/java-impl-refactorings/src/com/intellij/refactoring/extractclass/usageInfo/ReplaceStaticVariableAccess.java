// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.ArrayUtil;

public class ReplaceStaticVariableAccess extends FixableUsageInfo {
  private final PsiReferenceExpression expression;
  private final String delegateClass;
  private final boolean myEnumConstant;
  private static final Logger LOGGER = Logger.getInstance(ReplaceStaticVariableAccess.class);

  public ReplaceStaticVariableAccess(PsiReferenceExpression expression, String delegateClass, boolean enumConstant) {
    super(expression);
    this.expression = expression;
    this.delegateClass = delegateClass;
    myEnumConstant = enumConstant;
  }

  @Override
  public void fixUsage() {
    String name = expression.getReferenceName();
    if (myEnumConstant) {
      final PsiSwitchLabelStatementBase switchStatement = PsiTreeUtil.getParentOfType(expression, PsiSwitchLabelStatementBase.class);
      if (switchStatement != null && name != null) {
        MutationUtils.replaceExpression(name, expression);
        return;
      }
    }
    final boolean replaceWithGetEnumValue = myEnumConstant && !alreadyMigratedToEnum();
    final String link = replaceWithGetEnumValue ? "." + GenerateMembersUtil.suggestGetterName("value", expression.getType(), expression.getProject()) + "()" : "";
    MutationUtils.replaceExpression(delegateClass + '.' + name + link, expression);
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
