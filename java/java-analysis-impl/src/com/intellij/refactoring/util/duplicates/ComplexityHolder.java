// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ObjectIntHashMap;

import java.util.List;

/**
 * @author Pavel.Dolgov
 */
class ComplexityHolder {
  static final int MAX_ACCEPTABLE = 9;
  static final int TOO_COMPLEX = 100;

  private final ObjectIntHashMap<PsiExpression> myCache = new ObjectIntHashMap<>();
  private final List<PsiElement> myScope;

  ComplexityHolder(List<PsiElement> scope) {myScope = scope;}

  boolean isAcceptableExpression(PsiExpression expression) {
    return getComplexity(expression) <= MAX_ACCEPTABLE;
  }

  private int getComplexity(PsiExpression expression) {
    int complexity = myCache.get(expression);
    if (complexity < 0) {
      complexity = computeComplexity(expression);
      myCache.put(expression, complexity);
    }
    return complexity;
  }

  private int computeComplexity(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);

    if (expression instanceof PsiAssignmentExpression || expression instanceof PsiSuperExpression) {
      return TOO_COMPLEX;
    }

    if (expression instanceof PsiLiteralExpression || expression instanceof PsiQualifiedExpression) {
      return 1;
    }

    if (expression instanceof PsiUnaryExpression) {
      IElementType tokenType = ((PsiUnaryExpression)expression).getOperationTokenType();
      if (JavaTokenType.PLUSPLUS.equals(tokenType) || JavaTokenType.MINUSMINUS.equals(tokenType)) {
        return TOO_COMPLEX;
      }
      return 1 + getComplexity(((PsiUnaryExpression)expression).getOperand());
    }

    if (expression instanceof PsiBinaryExpression) {
      int complexity = 1 + getComplexity(((PsiBinaryExpression)expression).getLOperand());
      if (complexity > MAX_ACCEPTABLE) return complexity;
      return complexity + getComplexity(((PsiBinaryExpression)expression).getROperand());
    }

    if (expression instanceof PsiConditionalExpression) {
      int complexity = 1 + getComplexity(((PsiConditionalExpression)expression).getCondition());
      if (complexity > MAX_ACCEPTABLE) return complexity;

      complexity += getComplexity(((PsiConditionalExpression)expression).getThenExpression());
      if (complexity > MAX_ACCEPTABLE) return complexity;

      return complexity + getComplexity(((PsiConditionalExpression)expression).getElseExpression());
    }

    if (expression instanceof PsiArrayAccessExpression) {
      int complexity = 3 + getComplexity(((PsiArrayAccessExpression)expression).getArrayExpression());
      if (complexity > MAX_ACCEPTABLE) return complexity;

      return complexity + getComplexity(((PsiArrayAccessExpression)expression).getIndexExpression());
    }

    if (expression instanceof PsiReferenceExpression) {
      PsiElement resolved = ((PsiReferenceExpression)expression).resolve();
      if (resolved == null || isWithinScope(resolved)) {
        return TOO_COMPLEX;
      }
      if (resolved instanceof PsiVariable &&
          ((PsiVariable)resolved).hasModifierProperty(PsiModifier.STATIC) &&
          ((PsiVariable)resolved).hasModifierProperty(PsiModifier.FINAL)) {
        return 1;
      }

      PsiExpression qualifier = ((PsiReferenceExpression)expression).getQualifierExpression();
      if (qualifier == null) {
        return 2;
      }
      return 2 + getComplexity(qualifier);
    }

    if (expression instanceof PsiMethodCallExpression) {
      PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)expression).getMethodExpression();
      PsiElement resolved = methodExpression.resolve();
      if (resolved == null || isWithinScope(resolved)) {
        return TOO_COMPLEX;
      }
      int complexity = 3;
      PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null) {
        complexity += getComplexity(qualifier);
        if (complexity > MAX_ACCEPTABLE) {
          return complexity;
        }
      }
      PsiExpression[] arguments = ((PsiMethodCallExpression)expression).getArgumentList().getExpressions();
      for (PsiExpression argument : arguments) {
        complexity += getComplexity(argument);
        if (complexity > MAX_ACCEPTABLE) {
          return complexity;
        }
      }
      return complexity;
    }

    return TOO_COMPLEX;
  }

  private boolean isWithinScope(PsiElement resolved) {
    return DuplicatesFinder.isUnder(resolved, myScope);
  }

  public List<PsiElement> getScope() {
    return myScope;
  }
}
