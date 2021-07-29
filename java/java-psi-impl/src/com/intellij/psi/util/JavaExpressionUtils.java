// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiConstantEvaluationHelper;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.Nullable;

public class JavaExpressionUtils {

  private JavaExpressionUtils() { }

  @Nullable
  public static Object computeConstantExpression(@Nullable PsiExpression expression) {
    return computeConstantExpression(expression, false);
  }

  @Nullable
  public static Object computeConstantExpression(@Nullable PsiExpression expression, boolean throwConstantEvaluationOverflowException) {
    if (expression == null) {
      return null;
    }
    final Project project = expression.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiConstantEvaluationHelper constantEvaluationHelper = psiFacade.getConstantEvaluationHelper();
    return constantEvaluationHelper.computeConstantExpression(expression, throwConstantEvaluationOverflowException);
  }
}
