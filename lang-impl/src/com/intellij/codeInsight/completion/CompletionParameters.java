/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;

/**
 * @author peter
 */
public class CompletionParameters {
  private final PsiElement myPosition;

  public CompletionParameters(final PsiElement position) {
    myPosition = position;
  }

  public PsiElement getPosition() {
    return myPosition;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof CompletionParameters)) return false;

    final CompletionParameters that = (CompletionParameters)o;

    if (myPosition != null ? !myPosition.equals(that.myPosition) : that.myPosition != null) return false;

    return true;
  }

  public int hashCode() {
    return (myPosition != null ? myPosition.hashCode() : 0);
  }
}
