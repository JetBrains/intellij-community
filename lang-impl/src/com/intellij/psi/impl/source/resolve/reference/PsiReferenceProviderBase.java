/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiReferenceProviderBase implements PsiReferenceProvider {

  @NotNull
  public PsiReference[] getReferencesByString(String str, PsiElement position, int offsetInPosition) {
    return PsiReference.EMPTY_ARRAY;
  }

}
