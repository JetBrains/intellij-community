// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConstantExpressionUtil {
  public static Object computeCastTo(@Nullable PsiExpression expression, @Nullable PsiType castTo) {
    if (expression == null) return null;
    Object value = JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper().computeConstantExpression(expression, false);
    if(value == null) return null;
    return computeCastTo(value, castTo);
  }

  public static Object computeCastTo(@NotNull Object operand, @Nullable PsiType castType) {
    return TypeConversionUtil.computeCastTo(operand, castType);
  }

}
