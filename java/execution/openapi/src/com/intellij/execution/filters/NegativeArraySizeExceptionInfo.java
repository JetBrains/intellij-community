// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NegativeArraySizeExceptionInfo extends ExceptionInfo {
  NegativeArraySizeExceptionInfo(int offset, String message) {
    super(offset, "java.lang.NegativeArraySizeException", message);
  }

  public @Nullable Integer getSuppliedSize() {
    try {
      return Integer.valueOf(getExceptionMessage());
    }
    catch (NumberFormatException e) {
      return null;
    }
  }
  
  @Override
  PsiElement matchSpecificExceptionElement(@NotNull PsiElement e) {
    PsiExpression candidate = null;
    if (e instanceof PsiKeyword && e.textMatches(PsiKeyword.NEW) && e.getParent() instanceof PsiNewExpression) {
      PsiExpression[] dimensions = ((PsiNewExpression)e.getParent()).getArrayDimensions();
      for (PsiExpression dimension : dimensions) {
        if (dimension != null) {
          PsiLiteral literal = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(dimension), PsiLiteral.class);
          // Explicit negative number like -1 cannot be literal, it's unary expression
          if (literal != null && literal.getValue() instanceof Integer) continue;
        }
        if (candidate == null) {
          candidate = dimension;
        } else {
          return null;
        }
      }
    }
    return candidate;
  }
}
