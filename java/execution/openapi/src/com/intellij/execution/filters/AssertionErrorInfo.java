// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.java.syntax.parser.JavaKeywords;
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
  ExceptionLineRefiner.RefinerMatchResult matchSpecificExceptionElement(@NotNull PsiElement e) {
    if (e instanceof PsiKeyword && e.textMatches(JavaKeywords.ASSERT)) {
      PsiAssertStatement statement = ObjectUtils.tryCast(e.getParent(), PsiAssertStatement.class);
      if (statement == null) {
        return null;
      }
      return ExceptionLineRefiner.RefinerMatchResult.of(statement);
    }
    return null;
  }
}
