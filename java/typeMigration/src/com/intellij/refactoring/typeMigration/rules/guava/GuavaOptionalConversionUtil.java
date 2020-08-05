// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.*;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.Matcher;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public final class GuavaOptionalConversionUtil {
  static boolean isOptionalOrContext(@Nullable PsiExpression context) {
    if (context == null) return false;
    final PsiElement parent = context.getParent();
    if (parent == null) return false;
    final PsiElement maybeMethodCall = parent.getParent();
    if (!(maybeMethodCall instanceof PsiMethodCallExpression)) return false;
    final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)maybeMethodCall;
    final int argumentLength = methodCall.getArgumentList().getExpressionCount();
    if (argumentLength != 1) return false;
    final PsiMethod resolvedMethod = methodCall.resolveMethod();
    if (resolvedMethod == null || !"or".equals(resolvedMethod.getName())) return false;
    final PsiClass aClass = resolvedMethod.getContainingClass();
    return aClass != null && GuavaOptionalConversionRule.GUAVA_OPTIONAL.equals(aClass.getQualifiedName());
  }

  static String simplifyParameterPattern(PsiMethodCallExpression methodCall) {
    final PsiExpressionList argumentList = methodCall.getArgumentList();
    final PsiExpression[] expressions = argumentList.getExpressions();
    if (expressions.length == 1) {
      final PsiExpression expression = expressions[0];
      final MatchOptions options = new MatchOptions();
      options.setSearchPattern(GuavaOptionalConversionRule.OPTIONAL_CONVERTOR_PATTERN);
      options.setFileType(JavaFileType.INSTANCE);
      final Matcher matcher = new Matcher(methodCall.getProject(), options);
      final List<MatchResult> results = matcher.testFindMatches(expression.getText(), false, JavaFileType.INSTANCE, false);
      if (!results.isEmpty()) {
        final MatchResult result = results.get(0);
        if (result.getStart() == 0 && result.getEnd() == -1) {
          return GuavaOptionalConversionRule.OPTIONAL_CONVERTOR_PATTERN;
        }
      }
    }
    return "$o$";
  }
}
