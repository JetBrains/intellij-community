// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

public class ArrayStoreExceptionInfo extends ExceptionInfo {
  ArrayStoreExceptionInfo(int offset, String message) {
    super(offset, "java.lang.ArrayStoreException", message);
  }

  @Override
  boolean isSpecificExceptionElement(PsiElement e) {
    if (e instanceof PsiJavaToken && e.textMatches("=") && e.getParent() instanceof PsiAssignmentExpression) {
      PsiExpression lExpression = ((PsiAssignmentExpression)e.getParent()).getLExpression();
      return PsiUtil.skipParenthesizedExprDown(lExpression) instanceof PsiArrayAccessExpression;
    }
    return false;
  }
}
