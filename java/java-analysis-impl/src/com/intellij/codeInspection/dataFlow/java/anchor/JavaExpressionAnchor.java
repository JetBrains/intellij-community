// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.anchor;

import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

/**
 * An anchor that points to the Java expression, whose result is being evaluated 
 * by a corresponding instruction
 */
public final class JavaExpressionAnchor extends JavaDfaAnchor {
  private final @NotNull PsiExpression myExpression;

  public JavaExpressionAnchor(@NotNull PsiExpression expression) {
    myExpression = expression;
  }

  /**
   * @return expression whose result is being evaluated
   */
  public @NotNull PsiExpression getExpression() {
    return myExpression;
  }

  @Override
  public String toString() {
    return myExpression.getText();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JavaExpressionAnchor anchor = (JavaExpressionAnchor)o;
    return myExpression.equals(anchor.myExpression);
  }

  @Override
  public int hashCode() {
    return myExpression.hashCode();
  }
}
