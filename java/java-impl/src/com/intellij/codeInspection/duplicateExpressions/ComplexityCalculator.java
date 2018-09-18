// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.PsiBinaryExpression.BOOLEAN_OPERATION_TOKENS;

/**
 * @author Pavel.Dolgov
 */
class ComplexityCalculator {
  public static final int TOO_COMPLEX = 100_000;

  private static final int IDENTIFIER = 10;
  private static final int CONSTANT = 1;
  private static final int OPERATOR = 10;
  private static final int SIMPLE_OPERATOR = 2;
  private static final int TERNARY_OPERATOR = 20;
  private static final int METHOD_CALL = 20;
  private static final int SLICE = 10;
  private static final int LAMBDA = 20;
  private static final int REFERENCE = 20;
  private static final int PARAMETER = 1;

  private static final TokenSet NEGATIONS = TokenSet.create(JavaTokenType.EXCL, JavaTokenType.MINUS, JavaTokenType.TILDE);

  private final ObjectIntHashMap<PsiExpression> myCache = new ObjectIntHashMap<>();

  int getComplexity(@Nullable PsiExpression expression) {
    if (expression == null) {
      return 0;
    }

    int complexity = myCache.get(expression, -1);
    if (complexity < 0) {
      complexity = calculateComplexity(expression);
      myCache.put(expression, complexity);
    }
    return complexity;
  }

  private int calculateComplexity(@NotNull PsiExpression e) {
    if (e instanceof PsiThisExpression) {
      return 0;
    }
    if (e instanceof PsiLiteralExpression) {
      return CONSTANT;
    }
    if (e instanceof PsiInstanceOfExpression ||
        e instanceof PsiTypeCastExpression ||
        e instanceof PsiClassObjectAccessExpression ||
        e instanceof PsiQualifiedExpression) {
      return IDENTIFIER;
    }
    if (e instanceof PsiParenthesizedExpression) {
      return getComplexity(((PsiParenthesizedExpression)e).getExpression());
    }
    if (e instanceof PsiUnaryExpression) {
      PsiUnaryExpression unary = (PsiUnaryExpression)e;
      int c = NEGATIONS.contains(unary.getOperationTokenType()) ? SIMPLE_OPERATOR : OPERATOR;
      return getComplexity(unary.getOperand()) + c;
    }
    if (e instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)e;
      int c = BOOLEAN_OPERATION_TOKENS.contains(polyadic.getOperationTokenType()) ? SIMPLE_OPERATOR : OPERATOR;
      for (PsiExpression operand : polyadic.getOperands()) {
        c += getComplexity(operand);
      }
      return c;
    }
    if (e instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditional = (PsiConditionalExpression)e;
      return TERNARY_OPERATOR +
             getComplexity(conditional.getCondition()) +
             getComplexity(conditional.getThenExpression()) +
             getComplexity(conditional.getElseExpression());
    }
    if (e instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)e;
      PsiReferenceExpression ref = call.getMethodExpression();
      int c = getComplexity(ref.getQualifierExpression()) + METHOD_CALL;
      for (PsiExpression argument : call.getArgumentList().getExpressions()) {
        c += PARAMETER + getComplexity(argument);
      }
      return c;
    }
    if (e instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)e;
      PsiElement resolved = ref.resolve();
      int w = REFERENCE;
      if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) {
        w = IDENTIFIER;
      }
      else if (resolved instanceof PsiField) {
        PsiField field = (PsiField)resolved;
        if (field.hasModifierProperty(PsiModifier.FINAL)) {
          w = field.hasModifierProperty(PsiModifier.STATIC) ? CONSTANT : IDENTIFIER;
        }
      }
      return getComplexity(ref.getQualifierExpression()) + w;
    }
    if (e instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression access = (PsiArrayAccessExpression)e;
      return SLICE + getComplexity(access.getArrayExpression()) + getComplexity(access.getIndexExpression());
    }
    if (e instanceof PsiLambdaExpression) {
      PsiLambdaExpression lambda = (PsiLambdaExpression)e;
      PsiExpression bodyExpr = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      if (bodyExpr == null) return TOO_COMPLEX;
      return LAMBDA + getComplexity(bodyExpr) + lambda.getParameterList().getParametersCount() * PARAMETER;
    }
    if (e instanceof PsiArrayInitializerExpression) {
      PsiExpression[] initializers = ((PsiArrayInitializerExpression)e).getInitializers();
      int c = 0;
      for (PsiExpression initializer : initializers) {
        c += PARAMETER + getComplexity(initializer);
      }
      return c;
    }
    return TOO_COMPLEX;
  }
}
