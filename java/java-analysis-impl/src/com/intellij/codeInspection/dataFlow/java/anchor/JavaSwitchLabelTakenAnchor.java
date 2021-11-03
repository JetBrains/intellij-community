// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.anchor;

import com.intellij.psi.PsiCaseLabelElement;
import org.jetbrains.annotations.NotNull;

/**
 * An anchor of the boolean expression that says whether the switch label is taken
 */
public class JavaSwitchLabelTakenAnchor extends JavaDfaAnchor {
  private final @NotNull PsiCaseLabelElement myLabelElement;

  public JavaSwitchLabelTakenAnchor(@NotNull PsiCaseLabelElement labelElement) {
    myLabelElement = labelElement;
  }

  /**
   * @return switch label element
   */
  public @NotNull PsiCaseLabelElement getLabelElement() {
    return myLabelElement;
  }

  @Override
  public String toString() {
    return "Label: " + myLabelElement.getText();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JavaSwitchLabelTakenAnchor anchor = (JavaSwitchLabelTakenAnchor)o;
    return myLabelElement.equals(anchor.myLabelElement);
  }

  @Override
  public int hashCode() {
    return myLabelElement.hashCode() + 2;
  }
}
