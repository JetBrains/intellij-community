// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaFactType;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.*;

/**
 * Verifies that the expression is free of side effects and always yields the same result (in terms of <code>Object.equals()</code>),
 * so that it's safe to compute the expression only once and then reuse the result.<br>
 * Well-known APIs, such as <code>Object.equals()</code>, <code>Object.hashCode()</code>, <code>Object.toString()</code>,
 * <code>Comparable.compareTo()</code>, and <code>Comparator.compare()</code> are considered safe because of their contract.
 * Immutable classes like <code>String</code>, <code>BigDecimal</code>, etc, and utility classes like
 * <code>Objects</code>, <code>Math</code> (except <code>random()</code>) are OK too.
 *
 *  @author Pavel.Dolgov
 */
class SideEffectCalculator {
  private final ObjectIntHashMap<PsiExpression> myCache = new ObjectIntHashMap<>();

  private static final Set<String> SIDE_EFFECTS_FREE_CLASSES = ContainerUtil.set(
    JAVA_LANG_BOOLEAN,
    JAVA_LANG_CHARACTER,
    JAVA_LANG_SHORT,
    JAVA_LANG_INTEGER,
    JAVA_LANG_LONG,
    JAVA_LANG_FLOAT,
    JAVA_LANG_DOUBLE,
    JAVA_LANG_BYTE,
    JAVA_LANG_STRING,
    "java.math.BigDecimal",
    "java.math.BigInteger",
    "java.math.MathContext",
    "java.util.UUID",
    JAVA_UTIL_OBJECTS);

  @Contract("null -> false")
  boolean mayHaveSideEffect(@Nullable PsiExpression expression) {
    if (expression == null ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiClassObjectAccessExpression) {
      return false;
    }
    int c = myCache.get(expression);
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
      return PsiUtil.isIncrementDecrementOperation(e) || mayHaveSideEffect(((PsiUnaryExpression)e).getOperand());
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
      return false; // lambda itself (unless called) has no side effect
    }
    if (e instanceof PsiNewExpression) {
      return calculateNewSideEffect((PsiNewExpression)e);
    }
    return true;
  }

  private boolean calculateReferenceSideEffect(@NotNull PsiReferenceExpression ref) {
    if (mayHaveSideEffect(ref.getQualifierExpression())) {
      return true;
    }
    PsiElement resolved = ref.resolve();
    if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter || resolved instanceof PsiClass) {
      return false;
    }
    if (resolved instanceof PsiField) {
      PsiField field = (PsiField)resolved;
      return !field.hasModifierProperty(PsiModifier.FINAL);
    }
    if (resolved instanceof PsiMethod) {
      PsiMethod method = (PsiMethod)resolved;
      return methodMayHaveSideEffect(method);
    }
    return true;
  }

  private boolean calculateCallSideEffect(@NotNull PsiMethodCallExpression call) {
    PsiMethod method = call.resolveMethod();
    return methodMayHaveSideEffect(method) ||
           calculateSideEffect(call, call.getMethodExpression().getQualifierExpression());
  }

  private boolean calculateNewSideEffect(@NotNull PsiNewExpression newExpr) {
    if (newExpr.getAnonymousClass() != null ||
        mayHaveSideEffect(newExpr.getArrayInitializer())) {
      return true;
    }
    PsiJavaCodeReferenceElement ref = newExpr.getClassReference();
    if (ref != null) {
      PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiClass) {
        PsiClass psiClass = (PsiClass)resolved;
        return !ClassUtils.isImmutableClass(psiClass) ||
               calculateSideEffect(newExpr, newExpr.getQualifier());
      }
    }
    return true;
  }

  private boolean calculateSideEffect(@NotNull PsiCallExpression call, @Nullable PsiExpression qualifier) {
    PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList != null) {
      for (PsiExpression argument : argumentList.getExpressions()) {
        if (mayHaveSideEffect(argument)) return true;
      }
    }
    return mayHaveSideEffect(qualifier);
  }

  @Contract("null -> true")
  private static boolean methodMayHaveSideEffect(@Nullable PsiMethod method) {
    if (method == null) return true;
    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return true;

    String className = psiClass.getQualifiedName();
    if (className == null) return true;
    if (MethodUtils.isEquals(method) ||
        MethodUtils.isHashCode(method) ||
        MethodUtils.isToString(method) ||
        MethodUtils.isCompareTo(method) ||
        MethodUtils.isComparatorCompare(method) ||
        SIDE_EFFECTS_FREE_CLASSES.contains(className)) {
      return false;
    }

    if (JAVA_LANG_MATH.equals(className) ||
        JAVA_LANG_STRICT_MATH.equals(className)) {
      return "random".equals(method.getName()); // it's the only exception
    }
    if (JAVA_UTIL_COLLECTIONS.equals(className)) {
      String name = method.getName();
      return !name.equals("min") && !name.equals("max") && !name.startsWith("unmodifiable");
    }
    return true;
  }

  /**
   * Quick check to filter out the obvious things early
   */
  static boolean isDefinitelyWithSideEffect(@Nullable PsiExpression expression) {
    return expression instanceof PsiAssignmentExpression ||
           PsiUtil.isIncrementDecrementOperation(expression);
  }
}
