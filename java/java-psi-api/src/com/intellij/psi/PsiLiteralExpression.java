// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

/**
 * Represents a Java literal expression.
 */
public interface PsiLiteralExpression extends PsiExpression, PsiLiteral {

  default boolean isTextBlock() {
    return false;
  }
}
