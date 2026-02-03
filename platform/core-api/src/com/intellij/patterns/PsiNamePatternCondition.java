// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.patterns;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PsiNamePatternCondition<T extends PsiElement> extends PropertyPatternCondition<T, String> {

  public PsiNamePatternCondition(@NonNls String methodName, final ElementPattern<String> namePattern) {
    super(methodName, namePattern);
  }

  public ElementPattern<String> getNamePattern() {
    return getValuePattern();
  }

  @Override
  public String getPropertyValue(final @NotNull Object o) {
    return o instanceof PsiNamedElement ? ((PsiNamedElement)o).getName() : null;
  }

}
