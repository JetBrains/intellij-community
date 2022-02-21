// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashingStrategy;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */ 
final class ExpressionHashingStrategy implements HashingStrategy<PsiExpression> {
  private static final EquivalenceChecker EQUIVALENCE_CHECKER = new NoSideEffectExpressionEquivalenceChecker();

  @Override
  public boolean equals(PsiExpression e1, PsiExpression e2) {
    return EQUIVALENCE_CHECKER.expressionsAreEquivalent(e1, e2);
  }

  @Override
  public int hashCode(PsiExpression e) {
    if (e == null) {
      return 0;
    }
    if (e instanceof PsiParenthesizedExpression) {
      return hashCode(((PsiParenthesizedExpression)e).getExpression());
    }
    if (e instanceof PsiUnaryExpression) {
      PsiUnaryExpression unary = (PsiUnaryExpression)e;
      return hashCode(unary.getOperationTokenType(), unary.getOperand());
    }
    if (e instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)e;
      return hashCode(polyadic.getOperationTokenType(), polyadic.getOperands());
    }
    if (e instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditional = (PsiConditionalExpression)e;
      return hashCode(JavaTokenType.QUEST, conditional.getCondition(),
                             conditional.getThenExpression(), conditional.getElseExpression());
    }
    if (e instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)e;
      PsiReferenceExpression ref = call.getMethodExpression();
      return hashCode(ref.getReferenceName(), ref.getQualifierExpression(), call.getArgumentList().getExpressions());
    }
    if (e instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)e;
      return hashCode(ref.getReferenceName(), ref.getQualifierExpression());
    }
    if (e instanceof PsiInstanceOfExpression) {
      PsiInstanceOfExpression instanceOf = (PsiInstanceOfExpression)e;
      return hashCode(getTypeName(instanceOf.getCheckType()), instanceOf.getOperand());
    }
    if (e instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression access = (PsiArrayAccessExpression)e;
      return hashCode(JavaTokenType.LBRACKET, access.getArrayExpression(), access.getIndexExpression());
    }
    if (e instanceof PsiClassObjectAccessExpression) {
      String name = getTypeName(((PsiClassObjectAccessExpression)e).getOperand());
      return name != null ? name.hashCode() : 0;
    }
    if (e instanceof PsiLambdaExpression) {
      PsiExpression bodyExpr = LambdaUtil.extractSingleExpressionFromBody(((PsiLambdaExpression)e).getBody());
      return hashCode(JavaTokenType.ARROW, bodyExpr) * 31 + ((PsiLambdaExpression)e).getParameterList().getParametersCount();
    }
    if (e instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)e).getValue();
      return value != null ? value.hashCode() : 0;
    }
    return 0;
  }

  private int hashCode(@NotNull IElementType tokenType, PsiExpression @NotNull ... operands) {
    int hash = tokenType.hashCode();
    for (PsiExpression operand : operands) {
      hash = hash * 31 + hashCode(operand);
    }
    return hash;
  }

  private int hashCode(@Nullable String name, @Nullable PsiExpression expression, PsiExpression @NotNull ... operands) {
    int hash = name != null ? name.hashCode() : 0;
    hash = hash * 31 + hashCode(expression);
    for (PsiExpression operand : operands) {
      hash = hash * 31 + hashCode(operand);
    }
    return hash;
  }

  @Contract("null -> null")
  private static String getTypeName(@Nullable PsiTypeElement element) {
    if (element != null) {
      PsiJavaCodeReferenceElement reference = element.getInnermostComponentReferenceElement();
      if (reference != null) {
        return reference.getReferenceName();
      }
    }
    return null;
  }
}
