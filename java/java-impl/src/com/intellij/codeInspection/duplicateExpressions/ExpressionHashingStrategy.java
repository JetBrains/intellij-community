// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
class ExpressionHashingStrategy implements TObjectHashingStrategy<PsiExpression> {
  private static final EquivalenceChecker EQUIVALENCE_CHECKER = new NoSideEffectExpressionEquivalenceChecker();

  @Override
  public boolean equals(PsiExpression e1, PsiExpression e2) {
    return EQUIVALENCE_CHECKER.expressionsAreEquivalent(e1, e2);
  }

  @Override
  public int computeHashCode(PsiExpression e) {
    if (e == null) {
      return 0;
    }
    if (e instanceof PsiParenthesizedExpression) {
      return computeHashCode(((PsiParenthesizedExpression)e).getExpression());
    }
    if (e instanceof PsiUnaryExpression) {
      PsiUnaryExpression unary = (PsiUnaryExpression)e;
      return computeHashCode(unary.getOperationTokenType(), unary.getOperand());
    }
    if (e instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)e;
      return computeHashCode(polyadic.getOperationTokenType(), polyadic.getOperands());
    }
    if (e instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditional = (PsiConditionalExpression)e;
      return computeHashCode(JavaTokenType.QUEST, conditional.getCondition(),
                             conditional.getThenExpression(), conditional.getElseExpression());
    }
    if (e instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)e;
      PsiReferenceExpression ref = call.getMethodExpression();
      return computeHashCode(ref.getReferenceName(), ref.getQualifierExpression(), call.getArgumentList().getExpressions());
    }
    if (e instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)e;
      return computeHashCode(ref.getReferenceName(), ref.getQualifierExpression());
    }
    if (e instanceof PsiInstanceOfExpression) {
      PsiInstanceOfExpression instanceOf = (PsiInstanceOfExpression)e;
      return computeHashCode(getTypeName(instanceOf.getCheckType()), instanceOf.getOperand());
    }
    if (e instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression access = (PsiArrayAccessExpression)e;
      return computeHashCode(JavaTokenType.LBRACKET, access.getArrayExpression(), access.getIndexExpression());
    }
    if (e instanceof PsiClassObjectAccessExpression) {
      String name = getTypeName(((PsiClassObjectAccessExpression)e).getOperand());
      return name != null ? name.hashCode() : 0;
    }
    if (e instanceof PsiLambdaExpression) {
      PsiExpression bodyExpr = LambdaUtil.extractSingleExpressionFromBody(((PsiLambdaExpression)e).getBody());
      return computeHashCode(JavaTokenType.ARROW, bodyExpr) * 31 + ((PsiLambdaExpression)e).getParameterList().getParametersCount();
    }
    if (e instanceof PsiLiteralExpression) {
      Object value = ((PsiLiteralExpression)e).getValue();
      return value != null ? value.hashCode() : 0;
    }
    return 0;
  }

  private int computeHashCode(@NotNull IElementType tokenType, @NotNull PsiExpression... operands) {
    int hash = tokenType.hashCode();
    for (PsiExpression operand : operands) {
      hash = hash * 31 + computeHashCode(operand);
    }
    return hash;
  }

  private int computeHashCode(@Nullable String name, @Nullable PsiExpression expression, @NotNull PsiExpression... operands) {
    int hash = name != null ? name.hashCode() : 0;
    hash = hash * 31 + computeHashCode(expression);
    for (PsiExpression operand : operands) {
      hash = hash * 31 + computeHashCode(operand);
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
