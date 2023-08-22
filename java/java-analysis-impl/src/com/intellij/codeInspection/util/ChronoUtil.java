// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.util;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.callMatcher.CallHandler;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility class for work with <code>java.time</code> package
 */
public final class ChronoUtil {

  public static final String TEMPORAL_FIELD = "java.time.temporal.TemporalField";
  public static final String TEMPORAL_UNIT = "java.time.temporal.TemporalUnit";

  public static final String CHRONO_FIELD = "java.time.temporal.ChronoField";
  public static final String CHRONO_UNIT = "java.time.temporal.ChronoUnit";

  private static final CallMatcher FORMAT_PATTERN_METHOD_MATCHER = CallMatcher.anyOf(
    CallMatcher.instanceCall("java.text.SimpleDateFormat", "applyPattern", "applyLocalizedPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING),
    CallMatcher.staticCall("java.time.format.DateTimeFormatter", "ofPattern"),
    CallMatcher.instanceCall("java.time.format.DateTimeFormatterBuilder", "appendPattern").parameterTypes(CommonClassNames.JAVA_LANG_STRING)
  );
  public static final CallMatcher CHRONO_GET_MATCHERS = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_DATE, "get").parameterTypes(TEMPORAL_FIELD),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_DATE_TIME, "get").parameterTypes(TEMPORAL_FIELD),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_TIME, "get").parameterTypes(TEMPORAL_FIELD),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_OFFSET_TIME, "get").parameterTypes(TEMPORAL_FIELD),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_OFFSET_DATE_TIME, "get").parameterTypes(TEMPORAL_FIELD),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_ZONED_DATE_TIME, "get").parameterTypes(TEMPORAL_FIELD)
  );

  public static final CallMatcher CHRONO_GET_LONG_MATCHERS = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_DATE, "getLong").parameterTypes(TEMPORAL_FIELD),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_DATE_TIME, "getLong").parameterTypes(TEMPORAL_FIELD),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_TIME, "getLong").parameterTypes(TEMPORAL_FIELD),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_OFFSET_TIME, "getLong").parameterTypes(TEMPORAL_FIELD),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_OFFSET_DATE_TIME, "getLong").parameterTypes(TEMPORAL_FIELD),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_ZONED_DATE_TIME, "getLong").parameterTypes(TEMPORAL_FIELD)
  );

  public static final CallMatcher CHRONO_ALL_GET_MATCHERS = CallMatcher.anyOf(CHRONO_GET_MATCHERS, CHRONO_GET_LONG_MATCHERS);

  public static final CallMatcher CHRONO_WITH_MATCHERS = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_DATE, "with").parameterTypes(TEMPORAL_FIELD, "long"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_DATE_TIME, "with").parameterTypes(TEMPORAL_FIELD, "long"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_TIME, "with").parameterTypes(TEMPORAL_FIELD, "long"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_OFFSET_TIME, "with").parameterTypes(TEMPORAL_FIELD, "long"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_OFFSET_DATE_TIME, "with").parameterTypes(TEMPORAL_FIELD, "long"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_ZONED_DATE_TIME, "with").parameterTypes(TEMPORAL_FIELD, "long")
  );

  public static final CallMatcher CHRONO_PLUS_MINUS_MATCHERS = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_DATE, "plus", "minus").parameterTypes("long", TEMPORAL_UNIT),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_DATE_TIME, "plus", "minus").parameterTypes("long", TEMPORAL_UNIT),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_TIME, "plus", "minus").parameterTypes("long", TEMPORAL_UNIT),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_OFFSET_TIME, "plus", "minus").parameterTypes("long", TEMPORAL_UNIT),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_OFFSET_DATE_TIME, "plus", "minus").parameterTypes("long", TEMPORAL_UNIT),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_ZONED_DATE_TIME, "plus", "minus").parameterTypes("long", TEMPORAL_UNIT)
  );

  public static final Map<String, ChronoField> chronoFieldMap = Arrays.stream(ChronoField.values())
    .collect(Collectors.toMap(t -> t.name(), t -> t));
  private static final Map<String, ChronoUnit> chronoUnitMap = Arrays.stream(ChronoUnit.values())
    .collect(Collectors.toMap(t -> t.name(), t -> t));

  private static final CallMapper<ArgumentMatcher> SKIP_ARGUMENT_METHOD_HANDLER = new CallMapper<>(
    CallHandler.of(FORMAT_PATTERN_METHOD_MATCHER, methodCall -> argumentNumber(0, methodCall))
  );

  private static final Map<String, BiPredicate<PsiNewExpression, PsiElement>> SKIP_ARGUMENT_CONSTRUCTOR_HANDLER =
    Map.ofEntries(
      Map.entry("java.text.SimpleDateFormat", (expression, psiElement) -> argumentNumber(0, expression).test(psiElement))
    );

  private interface ArgumentMatcher extends Predicate<PsiElement> { }

  private static ArgumentMatcher argumentNumber(@SuppressWarnings("SameParameterValue") int number, @NotNull PsiCall callExpression) {
    return psiElement -> {
      PsiExpressionList argumentList = callExpression.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      PsiExpression[] expressions = argumentList.getExpressions();
      if (number < 0 || number >= expressions.length) {
        return false;
      }
      return PsiTreeUtil.isAncestor(expressions[number], psiElement, false);
    };
  }

  /**
   * @return {@code ChronoField} enum constant with the same name or null if enum constant is not found
   */
  @Nullable
  public static ChronoField getChronoField(@NotNull String name) {
    return chronoFieldMap.get(name);
  }

  /**
   * @return {@code ChronoUnit} enum constant with the same name or null if enum constant is not found
   */
  public static ChronoUnit getChronoUnit(String name) {
    return chronoUnitMap.get(name);
  }

  /**
   * @return true if the {@code method} (<code>with(ChronoField, long)</code>)
   * can be called with given {@code chronoField} without an exception, otherwise false
   */
  public static boolean isWithSupported(@Nullable PsiMethod method, @Nullable ChronoField chronoField) {
    if (method == null || chronoField == null) {
      return false;
    }
    if (!"with".equals(method.getName())) {
      return false;
    }
    if (!CHRONO_WITH_MATCHERS.methodMatches(method)) {
      return false;
    }
    String classQualifiedName = getQualifiedName(method);
    if (classQualifiedName == null) {
      return false;
    }
    if (!isAvailableByType(chronoField, classQualifiedName)) {
      return false;
    }
    return true;
  }

  /**
   * @return true if {@code literalExpression} is used as pattern for date formatters (SimpleDateFormat,  DateTimeFormatter)
   */
  public static boolean isPatternForDateFormat(@NotNull PsiLiteralExpression literalExpression) {
    PsiType type = literalExpression.getType();
    if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return false;
    }
    PsiElement element = ExpressionUtils.getPassThroughParent(literalExpression);
    if (element == null) {
      return false;
    }
    if (!(element instanceof PsiExpressionList expressionList)) {
      return false;
    }

    if (!(expressionList.getParent() instanceof PsiCall psiCall)) {
      return false;
    }

    if (psiCall instanceof PsiMethodCallExpression callExpression) {
      ArgumentMatcher matcher = SKIP_ARGUMENT_METHOD_HANDLER.mapFirst(callExpression);
      if (matcher == null) {
        return false;
      }
      return matcher.test(literalExpression);
    }
    if (psiCall instanceof PsiNewExpression newExpression) {
      PsiJavaCodeReferenceElement reference = newExpression.getClassReference();
      if (reference == null) {
        return false;
      }
      BiPredicate<PsiNewExpression, PsiElement> predicate =
        SKIP_ARGUMENT_CONSTRUCTOR_HANDLER.get(reference.getQualifiedName());
      if (predicate == null) {
        return false;
      }
      return predicate.test(newExpression, literalExpression);
    }
    return false;
  }

  @Nullable
  public static String getQualifiedName(@NotNull PsiMethod method) {
    return Optional.ofNullable(method.getContainingClass())
      .map(c -> c.getQualifiedName())
      .orElse(null);
  }

  /**
   * @return true if the {@code method} (<code>get(ChronoField)</code> or <code>getLong(ChronoField)</code>)
   * can be called with given {@code chronoField} without an exception, otherwise false
   */
  public static boolean isAnyGetSupported(@Nullable PsiMethod method, @Nullable ChronoField chronoField) {
    if (method == null || chronoField == null) {
      return false;
    }
    String methodName = method.getName();
    if (!"get".equals(methodName) && !"getLong".equals(methodName)) {
      return false;
    }
    if (!CHRONO_ALL_GET_MATCHERS.methodMatches(method)) {
      return false;
    }
    if ("get".equals(methodName) && !chronoField.range().isIntValue()) {
      return false;
    }
    String classQualifiedName = getQualifiedName(method);
    if (classQualifiedName == null) {
      return false;
    }
    if (!isAvailableByType(chronoField, classQualifiedName)) {
      return false;
    }
    return true;
  }

  /**
   * @return true if the {@code method} (<code>plus(long, ChronoUnit)</code> or <code>minus(long, ChronoUnit)</code>)
   * can be called with given {@code chronoUnit} without an exception, otherwise false
   */
  public static boolean isPlusMinusSupported(@Nullable PsiMethod method, @Nullable ChronoUnit chronoUnit) {
    if (method == null || chronoUnit == null) {
      return false;
    }
    String methodName = method.getName();
    if (!"plus".equals(methodName) && !"minus".equals(methodName)) {
      return false;
    }
    if (!CHRONO_PLUS_MINUS_MATCHERS.methodMatches(method)) {
      return false;
    }
    String classQualifiedName = getQualifiedName(method);
    if (classQualifiedName == null) {
      return false;
    }
    if (!isAvailableByType(chronoUnit, classQualifiedName)) {
      return false;
    }
    return true;
  }

  //easy to check
  @SuppressWarnings("DuplicateBranchesInSwitch")
  public static boolean isAvailableByType(@NotNull ChronoField chronoField, @NotNull String classQualifiedName) {
    return switch (classQualifiedName) {
      case CommonClassNames.JAVA_TIME_LOCAL_TIME -> chronoField.isTimeBased();
      case CommonClassNames.JAVA_TIME_LOCAL_DATE_TIME -> chronoField.isTimeBased() || chronoField.isDateBased();
      case CommonClassNames.JAVA_TIME_LOCAL_DATE -> chronoField.isDateBased();
      case CommonClassNames.JAVA_TIME_OFFSET_TIME -> chronoField.isTimeBased() || chronoField == ChronoField.OFFSET_SECONDS;
      case CommonClassNames.JAVA_TIME_OFFSET_DATE_TIME -> true;
      case CommonClassNames.JAVA_TIME_ZONED_DATE_TIME -> true;
      default -> false;
    };
  }

  //easy to check
  @SuppressWarnings("DuplicateBranchesInSwitch")
  private static boolean isAvailableByType(@NotNull ChronoUnit chronoUnit, @NotNull String classQualifiedName) {
    return switch (classQualifiedName) {
      case CommonClassNames.JAVA_TIME_LOCAL_TIME -> chronoUnit.isTimeBased();
      case CommonClassNames.JAVA_TIME_LOCAL_DATE_TIME -> chronoUnit != ChronoUnit.FOREVER;
      case CommonClassNames.JAVA_TIME_LOCAL_DATE -> chronoUnit.isDateBased();
      case CommonClassNames.JAVA_TIME_OFFSET_TIME -> chronoUnit.isTimeBased();
      case CommonClassNames.JAVA_TIME_OFFSET_DATE_TIME -> chronoUnit != ChronoUnit.FOREVER;
      case CommonClassNames.JAVA_TIME_ZONED_DATE_TIME -> chronoUnit != ChronoUnit.FOREVER;
      default -> false;
    };
  }
}
