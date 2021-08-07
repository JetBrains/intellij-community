// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.anchor;

import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

/**
 * An anchor of the boolean expression that says whether the switch label is taken
 */
public class JavaSwitchLabelTakenAnchor extends JavaDfaAnchor {
  private final @NotNull PsiExpression myExpression;

  public JavaSwitchLabelTakenAnchor(@NotNull PsiExpression expression) {
    myExpression = expression;
  }

  /**
   * @return switch label expression
   */
  public @NotNull PsiExpression getLabelExpression() {
    return myExpression;
  }

  @Override
  public String toString() {
    return "Label: " + myExpression.getText();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JavaSwitchLabelTakenAnchor anchor = (JavaSwitchLabelTakenAnchor)o;
    return myExpression.equals(anchor.myExpression);
  }

  @Override
  public int hashCode() {
    return myExpression.hashCode() + 2;
  }

}
