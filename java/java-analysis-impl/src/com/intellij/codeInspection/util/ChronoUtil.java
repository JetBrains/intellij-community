// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.util;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public final class ChronoUtil {

  private static final CallMatcher CHRONO_GET_MATCHERS = CallMatcher.anyOf(
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_DATE, "get", "getLong").parameterTypes("java.time.temporal.TemporalField"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_DATE_TIME, "get", "getLong")
      .parameterTypes("java.time.temporal.TemporalField"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_LOCAL_TIME, "get", "getLong").parameterTypes("java.time.temporal.TemporalField"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_OFFSET_TIME, "get", "getLong").parameterTypes("java.time.temporal.TemporalField"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_OFFSET_DATE_TIME, "get", "getLong")
      .parameterTypes("java.time.temporal.TemporalField"),
    CallMatcher.instanceCall(CommonClassNames.JAVA_TIME_ZONED_DATE_TIME, "get", "getLong")
      .parameterTypes("java.time.temporal.TemporalField")
  );

  private static final Map<String, ChronoField> chronoFieldMap = Arrays.stream(ChronoField.values())
    .collect(Collectors.toMap(t -> t.name(), t -> t));

  @Nullable
  public static ChronoField getChronoField(@NotNull String name) {
    return chronoFieldMap.get(name);
  }

  public static boolean isGetSupported(@NotNull PsiMethod method, @NotNull ChronoField chronoField) {
    if (!CHRONO_GET_MATCHERS.methodMatches(method)) {
      return false;
    }
    String methodName = method.getName();
    if ("getInt".equals(methodName) && !chronoField.range().isIntValue()) {
      return false;
    }
    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    String classQualifiedName = containingClass.getQualifiedName();
    if (chronoField.isDateBased() && (
      CommonClassNames.JAVA_TIME_OFFSET_TIME.equals(classQualifiedName) ||
      CommonClassNames.JAVA_TIME_LOCAL_TIME.equals(classQualifiedName))) {
      return false;
    }
    if (chronoField.isTimeBased() && CommonClassNames.JAVA_TIME_LOCAL_DATE.equals(classQualifiedName)) {
      return false;
    }
    if (chronoField == ChronoField.OFFSET_SECONDS &&
        !(CommonClassNames.JAVA_TIME_OFFSET_TIME.equals(classQualifiedName) ||
          CommonClassNames.JAVA_TIME_OFFSET_DATE_TIME.equals(classQualifiedName) ||
          CommonClassNames.JAVA_TIME_ZONED_DATE_TIME.equals(classQualifiedName))) {
      return false;
    }
    if (chronoField == ChronoField.INSTANT_SECONDS &&
        !(CommonClassNames.JAVA_TIME_OFFSET_DATE_TIME.equals(classQualifiedName) ||
          CommonClassNames.JAVA_TIME_ZONED_DATE_TIME.equals(classQualifiedName))) {
      return false;
    }
    return true;
  }
}
