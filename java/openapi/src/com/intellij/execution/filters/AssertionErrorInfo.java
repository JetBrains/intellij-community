// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;

public class AssertionErrorInfo extends ExceptionInfo {
  AssertionErrorInfo(int offset, String message) {
    super(offset, "java.lang.ArrayStoreException", message);
  }

  @Override
  boolean isSpecificExceptionElement(PsiElement e) {
    return e instanceof PsiKeyword && e.textMatches(PsiKeyword.ASSERT);
  }
}
