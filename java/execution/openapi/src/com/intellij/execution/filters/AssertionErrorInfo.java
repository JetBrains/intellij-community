// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.psi.PsiAssertStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class AssertionErrorInfo extends ExceptionInfo {
  AssertionErrorInfo(int offset, String message) {
    super(offset, "java.lang.AssertionError", message);
  }

  @Override
  PsiElement matchSpecificExceptionElement(@NotNull PsiElement e) {
    if (e instanceof PsiKeyword && e.textMatches(PsiKeyword.ASSERT)) {
      return ObjectUtils.tryCast(e.getParent(), PsiAssertStatement.class);
    }
    return null;
  }
}
