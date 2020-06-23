// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class ArrayStoreExceptionInfo extends ExceptionInfo {
  ArrayStoreExceptionInfo(int offset, String message) {
    super(offset, "java.lang.ArrayStoreException", message);
  }

  @Override
  PsiElement matchSpecificExceptionElement(@NotNull PsiElement e) {
    if (e instanceof PsiJavaToken && e.textMatches("=") && e.getParent() instanceof PsiAssignmentExpression) {
      PsiExpression lExpression = ((PsiAssignmentExpression)e.getParent()).getLExpression();
      if (PsiUtil.skipParenthesizedExprDown(lExpression) instanceof PsiArrayAccessExpression) {
        return e;
      }
    }
    return null;
  }
}
