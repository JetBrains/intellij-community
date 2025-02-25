// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeserver.core;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities related to Java expressions
 */
public final class JavaPsiExpressionUtil {
  /**
   * @param expression expression to check
   * @return true if the expression is a null literal (possibly parenthesized or cast)
   */
  @Contract("null -> false")
  public static boolean isNullLiteral(@Nullable PsiExpression expression) {
    return PsiUtil.deparenthesizeExpression(expression) instanceof PsiLiteralExpression literal && literal.getValue() == null;
  }
}
