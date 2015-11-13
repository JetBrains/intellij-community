/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMatcherExpression;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.MatchResult;
import com.intellij.structuralsearch.Matcher;
import com.intellij.structuralsearch.plugin.replace.impl.Replacer;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Dmitry Batkovich
 */
public class GuavaOptionalConversionUtil {
  static boolean isOptionalOrContext(@Nullable PsiExpression context) {
    if (context == null) return false;
    final PsiElement parent = context.getParent();
    if (parent == null) return false;
    final PsiElement maybeMethodCall = parent.getParent();
    if (!(maybeMethodCall instanceof PsiMethodCallExpression)) return false;
    final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)maybeMethodCall;
    final int argumentLength = methodCall.getArgumentList().getExpressions().length;
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
      Matcher matcher = new Matcher(methodCall.getProject());
      final MatchOptions options = new MatchOptions();
      options.setFileType(StdFileTypes.JAVA);
      final List<MatchResult> results =
        matcher.testFindMatches(expression.getText(), GuavaOptionalConversionRule.OPTIONAL_CONVERTOR_PATTERN, options, false);
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
