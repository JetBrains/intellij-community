// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.PsiBinaryExpression.BOOLEAN_OPERATION_TOKENS;

/**
 * Calculates how big is the expression. Is used for filtering out too simple expressions. <br>
 * Expression complexity is >= 0. Complexity <= 10 means the expression is trivial (a constant or a variable). <br>
 * Complexity >= 100 means that the expression is really big (many operators and/or method calls). <br>
 * Side effects aren't checked here.
 *
 * @author Pavel.Dolgov
 */
class ComplexityCalculator {
  private static final int IDENTIFIER = 10;
  private static final int QUALIFIER = 6;
  private static final int CONSTANT = 1;
  private static final int OPERATOR = 10;
  private static final int SIMPLE_OPERATOR = 2;
  private static final int TERNARY_OPERATOR = 20;
  private static final int METHOD_CALL = 20;
  private static final int SLICE = 10;
  private static final int LAMBDA = 20;
  private static final int REFERENCE = 20;
  private static final int NEW_EXPR = 20;
  private static final int PARAMETER = 1;
  private static final int UNKNOWN = 15;
  private static final int PATH_CONSTRUCTION = 71;

  private static final TokenSet NEGATIONS = TokenSet.create(JavaTokenType.EXCL, JavaTokenType.MINUS, JavaTokenType.TILDE);

  private static final CallMatcher PATH_CONSTRUCTION_CALL = CallMatcher.anyOf(
    CallMatcher.staticCall("java.nio.file.Path", "of"),
    CallMatcher.staticCall("java.nio.file.Paths", "get")
  );

  private final ObjectIntHashMap<PsiExpression> myCache = new ObjectIntHashMap<>();

  int getComplexity(@Nullable PsiExpression expression) {
    if (expression == null) {
      return 0;
    }

    int complexity = myCache.get(expression);
    if (complexity < 0) {
      complexity = calculateComplexity(expression);
      myCache.put(expression, complexity);
    }
    return complexity;
  }

  private int calculateComplexity(@NotNull PsiExpression e) {
    if (e instanceof PsiLiteralExpression) {
      return CONSTANT;
    }
    if (e instanceof PsiInstanceOfExpression ||
        e instanceof PsiTypeCastExpression) {
      return IDENTIFIER;
    }
    if (e instanceof PsiClassObjectAccessExpression) {
      return QUALIFIER;
    }
    if (e instanceof PsiQualifiedExpression) {
      return ((PsiQualifiedExpression)e).getQualifier() != null ? QUALIFIER : 0;
    }
    if (e instanceof PsiParenthesizedExpression) {
      return getComplexity(((PsiParenthesizedExpression)e).getExpression());
    }
    if (e instanceof PsiUnaryExpression) {
      PsiUnaryExpression unary = (PsiUnaryExpression)e;
      int c = NEGATIONS.contains(unary.getOperationTokenType()) ? SIMPLE_OPERATOR : OPERATOR;
      return getComplexity(unary.getOperand()) + c;
    }
    if (e instanceof PsiBinaryExpression) {
      PsiBinaryExpression binary = (PsiBinaryExpression)e;
      IElementType token = binary.getOperationTokenType();
      PsiExpression left = binary.getLOperand();
      PsiExpression right = binary.getROperand();
      int c = BOOLEAN_OPERATION_TOKENS.contains(token) ||
              JavaTokenType.PLUS.equals(token) && (isLiteral(left, "1") || isLiteral(right, "1")) ||
              JavaTokenType.MINUS.equals(token) && isLiteral(right, "1") ||
              JavaTokenType.ASTERISK.equals(token) && (isLiteral(left, "2") || isLiteral(right, "2")) ||
              JavaTokenType.DIV.equals(token) && isLiteral(right, "2")
              ? SIMPLE_OPERATOR : OPERATOR;
      return c + getComplexity(left) + getComplexity(right);
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
      if (PATH_CONSTRUCTION_CALL.test(call)) return PATH_CONSTRUCTION;
      PsiReferenceExpression ref = call.getMethodExpression();
      return METHOD_CALL + getComplexity(ref.getQualifierExpression()) + calculateArgumentsComplexity(call.getArgumentList());
    }
    if (e instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)e;
      PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiClass) {
        return QUALIFIER;
      }
      int w = REFERENCE;
      if (PsiUtil.isJvmLocalVariable(resolved)) {
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
      PsiElement body = lambda.getBody();
      int c = LAMBDA + PARAMETER * lambda.getParameterList().getParametersCount();
      PsiExpression bodyExpr = LambdaUtil.extractSingleExpressionFromBody(body);
      if (bodyExpr != null) {
        return c + getComplexity(bodyExpr);
      }
      if(body instanceof PsiCodeBlock) {
        return c + UNKNOWN * ((PsiCodeBlock)body).getStatementCount();
      }
      return c + UNKNOWN;
    }
    if (e instanceof PsiArrayInitializerExpression) {
      PsiExpression[] initializers = ((PsiArrayInitializerExpression)e).getInitializers();
      int c = 0;
      for (PsiExpression initializer : initializers) {
        c += PARAMETER + getComplexity(initializer);
      }
      return c;
    }
    if (e instanceof PsiNewExpression) {
      PsiNewExpression newExpr = (PsiNewExpression)e;
      return NEW_EXPR +
             getComplexity(newExpr.getQualifier()) +
             getComplexity(newExpr.getArrayInitializer()) +
             calculateArgumentsComplexity(newExpr.getArgumentList()) +
             (newExpr.getAnonymousClass() != null ? UNKNOWN : 0);
    }
    return UNKNOWN;
  }

  private int calculateArgumentsComplexity(@Nullable PsiExpressionList arguments) {
    if (arguments == null) return 0;
    int c = 0;
    for (PsiExpression argument : arguments.getExpressions()) {
      c += PARAMETER + getComplexity(argument);
    }
    return c;
  }

  private static boolean isLiteral(@Nullable PsiExpression expression, @NotNull String withText) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    return expression instanceof PsiLiteral && expression.textMatches(withText);
  }

  /**
   * Quick check to filter out the obvious things early
   */
  static boolean isDefinitelySimple(@Nullable PsiExpression expression) {
    if (expression instanceof PsiLiteral) {
      return true;
    }
    if (expression instanceof PsiReferenceExpression) {
      PsiElement qualifier = ((PsiReferenceExpression)expression).getQualifier();
      if (qualifier instanceof PsiQualifiedExpression || !(qualifier instanceof PsiExpression)) {
        PsiElement resolved = ((PsiReferenceExpression)expression).resolve();
        return resolved instanceof PsiVariable || resolved instanceof PsiClass;
      }
    }
    return false;
  }
}
