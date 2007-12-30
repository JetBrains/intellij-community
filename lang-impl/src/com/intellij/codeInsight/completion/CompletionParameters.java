/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;

/**
 * @author peter
 */
public class CompletionParameters<Result> {
  private final Class<Result> myResultClass;
  private final PsiElement myPosition;

  public CompletionParameters(final Class<Result> resultClass, final PsiElement position) {
    myResultClass = resultClass;
    myPosition = position;
  }

  public PsiElement getPosition() {
    return myPosition;
  }

  public Class<Result> getResultClass() {
    return myResultClass;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof CompletionParameters)) return false;

    final CompletionParameters that = (CompletionParameters)o;

    if (!myResultClass.equals(that.myResultClass)) return false;

    return true;
  }

  public int hashCode() {
    return myResultClass.hashCode();
  }
}
