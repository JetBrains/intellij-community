// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.duplicateExpressions;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.GetterDescriptor;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiAssignmentExpression;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassObjectAccessExpression;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiInstanceOfExpression;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParenthesizedExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiUnaryExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_BOOLEAN;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_BYTE;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_CHARACTER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_DOUBLE;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_FLOAT;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_LONG;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_MATH;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_SHORT;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRICT_MATH;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTIONS;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_OBJECTS;

/**
 * Verifies that the expression is free of side effects and always yields the same result (in terms of {@code Object.equals()}),
 * so that it's safe to compute the expression only once and then reuse the result.<br>
 * Well-known APIs, such as {@code Object.equals()}, {@code Object.hashCode()}, {@code Object.toString()},
 * {@code Comparable.compareTo()}, and {@code Comparator.compare()} are considered safe because of their contract.
 * Immutable classes like {@code String}, {@code BigDecimal}, etc, and utility classes like
 * {@code Objects}, {@code Math} (except {@code random()}) are OK too.
 */
final class SideEffectCalculator {
  private final Object2IntMap<PsiExpression> myCache = new Object2IntOpenHashMap<>();

  private static final Set<String> SIDE_EFFECTS_FREE_CLASSES = Set.of(
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
    JAVA_UTIL_OBJECTS);

  SideEffectCalculator() {
    myCache.defaultReturnValue(-1);
  }

  @Contract("null -> false")
  boolean mayHaveSideEffect(@Nullable PsiExpression expression) {
    if (expression == null ||
        expression instanceof PsiLiteralExpression ||
        expression instanceof PsiClassObjectAccessExpression) {
      return false;
    }
    int c = myCache.getInt(expression);
    if (c < 0) {
      c = calculateSideEffect(expression) ? 1 : 0;
      myCache.put(expression, c);
    }
    return c == 1;
  }

  boolean calculateSideEffect(@Nullable PsiExpression e) {
    if (e instanceof PsiParenthesizedExpression paren) {
      return mayHaveSideEffect(paren.getExpression());
    }
    if (e instanceof PsiUnaryExpression) {
      return PsiUtil.isIncrementDecrementOperation(e) || mayHaveSideEffect(((PsiUnaryExpression)e).getOperand());
    }
    if (e instanceof PsiPolyadicExpression polyadic) {
      return ContainerUtil.exists(polyadic.getOperands(), this::mayHaveSideEffect);
    }
    if (e instanceof PsiConditionalExpression conditional) {
      return mayHaveSideEffect(conditional.getCondition()) ||
             mayHaveSideEffect(conditional.getThenExpression()) ||
             mayHaveSideEffect(conditional.getElseExpression());
    }
    if (e instanceof PsiMethodCallExpression call) {
      return calculateCallSideEffect(call);
    }
    if (e instanceof PsiReferenceExpression ref) {
      return calculateReferenceSideEffect(ref);
    }
    if (e instanceof PsiInstanceOfExpression instanceOf) {
      return mayHaveSideEffect(instanceOf.getOperand());
    }
    if (e instanceof PsiArrayAccessExpression access) {
      PsiExpression array = access.getArrayExpression();
      return mayHaveSideEffect(array) ||
             mayHaveSideEffect(access.getIndexExpression()) ||
             !CommonDataflow.getDfType(array).isLocal();
    }
    if (e instanceof PsiLambdaExpression) {
      return false; // lambda itself (unless called) has no side effect
    }
    if (e instanceof PsiNewExpression newExpression) {
      return calculateNewSideEffect(newExpression);
    }
    return true;
  }

  private boolean calculateReferenceSideEffect(@NotNull PsiReferenceExpression ref) {
    if (mayHaveSideEffect(ref.getQualifierExpression())) {
      return true;
    }
    PsiElement resolved = ref.resolve();
    if (resolved instanceof PsiLocalVariable || resolved instanceof PsiParameter || 
        resolved instanceof PsiClass || resolved instanceof PsiPackage) {
      return false;
    }
    if (resolved instanceof PsiField field) {
      return !field.hasModifierProperty(PsiModifier.FINAL);
    }
    if (resolved instanceof PsiMethod method) {
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
    return ref == null || !(ref.resolve() instanceof PsiClass psiClass) || 
           !ClassUtils.isImmutableClass(psiClass) || calculateSideEffect(newExpr, newExpr.getQualifier());
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
    if (className == null) {
      return true;
    }

    if (MethodUtils.isEquals(method) ||
        MethodUtils.isHashCode(method) ||
        MethodUtils.isToString(method) ||
        MethodUtils.isCompareTo(method) ||
        MethodUtils.isComparatorCompare(method) ||
        SIDE_EFFECTS_FREE_CLASSES.contains(className)) {
      return false;
    }

    if ("java.util.UUID".equals(className)) {
      return "randomUUID".equals(method.getName());
    }
    if (JAVA_LANG_MATH.equals(className) ||
        JAVA_LANG_STRICT_MATH.equals(className)) {
      return "random".equals(method.getName()); // it's the only exception
    }
    if (JAVA_UTIL_COLLECTIONS.equals(className)) {
      String name = method.getName();
      return !name.equals("min") && !name.equals("max") && !name.startsWith("unmodifiable");
    }
    if ("java.nio.file.Path".equals(className)) {
      return !"of".equals(method.getName());
    }
    if ("java.nio.file.Paths".equals(className)) {
      return !"get".equals(method.getName());
    }
    if (method.getParameterList().getParametersCount() == 0 && new GetterDescriptor(method).isStable()) {
      return false;
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
