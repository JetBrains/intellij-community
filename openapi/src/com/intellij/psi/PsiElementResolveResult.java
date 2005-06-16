/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Trivial implementation of the <code>{@link ResolveResult}</code>.
 */
public class PsiElementResolveResult implements ResolveResult{
  @NotNull private final PsiElement myElement;

  public PsiElementResolveResult(@NotNull PsiElement element) {
    myElement = element;
  }

  @NotNull public PsiElement getElement() {
    return myElement;
  }

  public boolean isValidResult() {
    return true;
  }
}
