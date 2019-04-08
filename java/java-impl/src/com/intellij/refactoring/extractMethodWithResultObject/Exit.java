// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * @author Pavel.Dolgov
 */
class Exit {
  private final ExitType myType;
  private final PsiElement myExitedElement;

  Exit(@NotNull ExitType type, @Nullable PsiElement element) {
    myType = type;
    myExitedElement = element;
  }

  ExitType getType() {
    return myType;
  }

  PsiElement getExitedElement() {
    return myExitedElement;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Exit)) return false;

    Exit exit = (Exit)o;
    return myType == exit.myType && Objects.equals(myExitedElement, exit.myExitedElement);
  }

  @Override
  public int hashCode() {
    return 31 * myType.hashCode() + (myExitedElement != null ? myExitedElement.hashCode() : 0);
  }

  @Override
  public String toString() {
    return myExitedElement != null ? myType + " " + myExitedElement : myType.toString();
  }
}
