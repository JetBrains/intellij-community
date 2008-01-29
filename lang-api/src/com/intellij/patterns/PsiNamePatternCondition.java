/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class PsiNamePatternCondition<T extends PsiElement> extends PropertyPatternCondition<T, String> {
  private final ElementPattern<String> myNamePattern;

  public PsiNamePatternCondition(final ElementPattern<String> namePattern) {
    super(namePattern);
    myNamePattern = namePattern;
  }

  public ElementPattern<String> getNamePattern() {
    return myNamePattern;
  }

  public String getPropertyValue(@NotNull final Object o) {
    return o instanceof PsiNamedElement ? ((PsiNamedElement)o).getName() : null;
  }

}
