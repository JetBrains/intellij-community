// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.psi.*;
import com.intellij.util.containers.ObjectIntHashMap;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.psiutils.MethodUtils.methodMatches;

/**
 * @author Pavel.Dolgov
 */
class SideEffectCalculator {
  private final ObjectIntHashMap<PsiExpression> myCache = new ObjectIntHashMap<>();

  @Contract("null -> false")
  boolean mayHaveSideEffect(@Nullable PsiExpression expression) {
    if (expression == null ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiClassObjectAccessExpression) {
      return false;
    }
    int c = myCache.get(expression, -1);
    if (c < 0) {
      c = calculateSideEffect(expression) ? 1 : 0;
      myCache.put(expression, c);
    }
    return c == 1;
  }

  boolean calculateSideEffect(@Nullable PsiExpression e) {
    if (e instanceof PsiParenthesizedExpression) {
      return mayHaveSideEffect(((PsiParenthesizedExpression)e).getExpression());
    }
    if (e instanceof PsiUnaryExpression) {
      PsiUnaryExpression unary = (PsiUnaryExpression)e;
      return mayHaveSideEffect(unary.getOperand());
    }
    if (e instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)e;
      return Arrays.stream(polyadic.getOperands()).anyMatch(this::mayHaveSideEffect);
    }
    if (e instanceof PsiConditionalExpression) {
      PsiConditionalExpression conditional = (PsiConditionalExpression)e;
      return mayHaveSideEffect(conditional.getCondition()) ||
             mayHaveSideEffect(conditional.getThenExpression()) ||
             mayHaveSideEffect(conditional.getElseExpression());
    }
    if (e instanceof PsiMethodCallExpression) {
      return calculateCallSideEffect((PsiMethodCallExpression)e);
    }
    if (e instanceof PsiReferenceExpression) {
      return calculateReferenceSideEffect((PsiReferenceExpression)e);
    }
    if (e instanceof PsiInstanceOfExpression) {
      PsiInstanceOfExpression instanceOf = (PsiInstanceOfExpression)e;
      return mayHaveSideEffect(instanceOf.getOperand());
    }
    if (e instanceof PsiArrayAccessExpression) {
      PsiArrayAccessExpression access = (PsiArrayAccessExpression)e;
      PsiExpression array = access.getArrayExpression();
      return mayHaveSideEffect(array) ||
             mayHaveSideEffect(access.getIndexExpression()) ||
             !Boolean.TRUE.equals(CommonDataflow.getExpressionFact(array, DfaFactType.LOCALITY));
    }
    if (e instanceof PsiLambdaExpression) {
      PsiLambdaExpression lambda = (PsiLambdaExpression)e;
      PsiExpression bodyExpr = LambdaUtil.extractSingleExpressionFromBody(lambda.getBody());
      return bodyExpr == null || // null means the body is a non-trivial code block
             mayHaveSideEffect(bodyExpr);
    }
    return true;
  }

  private boolean calculateReferenceSideEffect(@NotNull PsiReferenceExpression ref) {
    if (mayHaveSideEffect(ref.getQualifierExpression())) {
      return true;
    }
    PsiElement resolved = ref.resolve();
    if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter) {
      return false;
    }
    if (resolved instanceof PsiField) {
      PsiField field = (PsiField)resolved;
      return !field.hasModifierProperty(PsiModifier.FINAL);
    }
    if (resolved instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)resolved;
      String name = method.getName();
      if ("hashCode".equals(name) || "equals".equals(name)) {
        return true;
      }
      PsiClass psiClass = method.getContainingClass();
      return !(psiClass != null && ClassUtils.isImmutableClass(psiClass));
    }
    return true;
  }

  private boolean calculateCallSideEffect(@NotNull PsiMethodCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return true; // can't be sure
    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return true; // can't be sure

    if (ClassUtils.isImmutableClass(psiClass)) {
      return calculateCallPartsSideEffect(call);
    }

    int paramCount = method.getParameterList().getParametersCount();
    if (paramCount == 0 && methodMatches(method, null, PsiType.INT, "hashCode", (PsiType[])null) ||
        paramCount == 1 && methodMatches(method, null, PsiType.BOOLEAN, "equals", (PsiType[])null) ||
        paramCount == 1 && methodMatches(method, JAVA_LANG_COMPARABLE, PsiType.INT, "compareTo", (PsiType[])null) ||
        paramCount == 2 && methodMatches(method, JAVA_UTIL_COMPARATOR, PsiType.INT, "compare", (PsiType[])null)) {
      return calculateCallPartsSideEffect(call);
    }

    String className = psiClass.getQualifiedName();
    if (JAVA_LANG_MATH.equals(className) || JAVA_LANG_STRICT_MATH.equals(className)) {
      return !"random".equals(method.getName());
    }
    return true;
  }

  private boolean calculateCallPartsSideEffect(@NotNull PsiMethodCallExpression call) {
    for (PsiExpression argument : call.getArgumentList().getExpressions()) {
      if (mayHaveSideEffect(argument)) return true;
    }
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    return mayHaveSideEffect(qualifier);
  }
}
