// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.light;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

/**
 * Extension of the {@link LightVariableBuilder} that implements {@code equals} and {@code hashCode}
 */
public class DefiniteLightVariable extends LightVariableBuilder {
  public DefiniteLightVariable(@NotNull String name,
                               @NotNull PsiType type,
                               @NotNull PsiElement navigationElement) {
    super(name, type, navigationElement);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DefiniteLightVariable builder = (DefiniteLightVariable)o;
    return Objects.equals(getName(), builder.getName()) &&
           Objects.equals(getType(), builder.getType()) &&
           areModifiersEqual(builder.getModifierList()) &&
           Objects.equals(getBaseIcon(), builder.getBaseIcon()) &&
           Objects.equals(getOriginInfo(), builder.getOriginInfo()) &&
           areNavigationElementsEqual(builder);
  }

  private boolean areNavigationElementsEqual(DefiniteLightVariable another) {
    PsiElement navigationElement = getNavigationElement();
    if (navigationElement == this) return another.getNavigationElement() == another;
    return Objects.equals(navigationElement, another.getNavigationElement());
  }

  private boolean areModifiersEqual(@NotNull PsiModifierList modifierList) {
    PsiModifierList thisModifierList = getModifierList();
    if (thisModifierList instanceof LightModifierList && modifierList instanceof LightModifierList) {
      return Arrays.equals(((LightModifierList)thisModifierList).getModifiers(), ((LightModifierList)modifierList).getModifiers());
    }
    return Objects.equals(thisModifierList, modifierList);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getType(), getBaseIcon(), getOriginInfo());
  }
}
