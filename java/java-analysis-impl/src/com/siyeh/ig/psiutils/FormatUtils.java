// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FormatUtils {
  public static final CallMatcher STRING_FORMATTED = CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_STRING, "formatted")
    .parameterTypes("java.lang.Object...");


  /**
   */
  @NonNls
  public static final Set<String> formatMethodNames = new HashSet<>(2);
  /**
   */
  public static final Set<String> formatClassNames = new HashSet<>(4);

  static {
    formatMethodNames.add("format");
    formatMethodNames.add("printf");

    formatClassNames.add("java.io.Console");
    formatClassNames.add("java.io.PrintWriter");
    formatClassNames.add("java.io.PrintStream");
    formatClassNames.add("java.util.Formatter");
    formatClassNames.add(CommonClassNames.JAVA_LANG_STRING);
  }

  private FormatUtils() {}

  public static boolean isFormatCall(PsiMethodCallExpression expression) {
    return isFormatCall(expression, Collections.emptyList(), Collections.emptyList());
  }

  public static boolean isFormatCall(PsiMethodCallExpression expression, List<String> optionalMethods, List<String> optionalClasses) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if (!formatMethodNames.contains(name) && !optionalMethods.contains(name)) {
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
    return formatClassNames.contains(className) || optionalClasses.contains(className);
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

  @Nullable
  public static PsiExpression getFormatArgument(PsiExpressionList argumentList) {
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
