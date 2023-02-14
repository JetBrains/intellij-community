// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.HashingStrategy;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    if (e instanceof PsiUnaryExpression unary) {
      return hashCode(unary.getOperationTokenType(), unary.getOperand());
    }
    if (e instanceof PsiPolyadicExpression polyadic) {
      return hashCode(polyadic.getOperationTokenType(), polyadic.getOperands());
    }
    if (e instanceof PsiConditionalExpression conditional) {
      return hashCode(JavaTokenType.QUEST, conditional.getCondition(),
                             conditional.getThenExpression(), conditional.getElseExpression());
    }
    if (e instanceof PsiMethodCallExpression call) {
      PsiReferenceExpression ref = call.getMethodExpression();
      String name = ref.getReferenceName();
      PsiExpression qualifier = ref.getQualifierExpression();
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (qualifier != null) {
        if ("get".equals(name) && (qualifier.textMatches("Paths") || qualifier.textMatches("java.nio.file.Paths")) ||
            "of".equals(name) && qualifier.textMatches("java.nio.file.Path")) {
          return hashCode("of", "Path", args); 
        }
      }
      return hashCode(name, qualifier, args);
    }
    if (e instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)e;
      return hashCode(ref.getReferenceName(), ref.getQualifierExpression());
    }
    if (e instanceof PsiInstanceOfExpression instanceOf) {
      return hashCode(getTypeName(instanceOf.getCheckType()), instanceOf.getOperand());
    }
    if (e instanceof PsiArrayAccessExpression access) {
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

  private int hashCode(@Nullable String name, @NotNull String qualifier, PsiExpression @NotNull ... operands) {
    int hash = name != null ? name.hashCode() : 0;
    hash = hash * 31 + qualifier.hashCode() * 31;
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
