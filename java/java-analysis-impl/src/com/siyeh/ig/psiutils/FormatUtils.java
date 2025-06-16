// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class FormatUtils {
  public static final CallMatcher STRING_FORMATTED = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "formatted")
    .parameterTypes("java.lang.Object...");


  /**
   */
  private static final @NonNls @Unmodifiable Set<String> formatMethodNames;
  /**
   */
  private static final @Unmodifiable Set<String> formatClassNames;

  static {
    formatMethodNames = Set.of("format", "printf");

    formatClassNames = Set.of("java.io.Console",
    "java.io.PrintWriter",
    "java.io.PrintStream",
    "java.util.Formatter",
    CommonClassNames.JAVA_LANG_STRING);
  }

  private FormatUtils() {}

  public static boolean isFormatCall(PsiMethodCallExpression expression) {
    return isFormatCall(expression, Collections.emptyList(), Collections.emptyList());
  }

  public static boolean isFormatCall(PsiMethodCallExpression expression, @Unmodifiable List<String> optionalMethods,@Unmodifiable  List<String> optionalClasses) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if ((name == null || !formatMethodNames.contains(name)) && !optionalMethods.contains(name)) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String className = containingClass.getQualifiedName();
    return className != null && formatClassNames.contains(className) || optionalClasses.contains(className);
  }

  public static boolean isFormatCallArgument(PsiElement element) {
    final PsiExpressionList expressionList =
      PsiTreeUtil.getParentOfType(element, PsiExpressionList.class, true, PsiCodeBlock.class, PsiStatement.class, PsiClass.class);
    if (expressionList == null) {
      return false;
    }
    final PsiElement parent = expressionList.getParent();
    return parent instanceof PsiMethodCallExpression && isFormatCall((PsiMethodCallExpression)parent);
  }

  public static @Nullable PsiExpression getFormatArgument(PsiExpressionList argumentList) {
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length == 0) {
      return null;
    }
    final PsiExpression firstArgument = arguments[0];
    final PsiType type = firstArgument.getType();
    if (type == null) {
      return null;
    }
    final int formatArgumentIndex;
    if ("java.util.Locale".equals(type.getCanonicalText()) && arguments.length > 1) {
      formatArgumentIndex = 1;
    }
    else {
      formatArgumentIndex = 0;
    }
    return arguments[formatArgumentIndex];
  }
}
