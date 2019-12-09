// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class CompletionConfidence {

  /**
   * @deprecated not used anymore, only the user controls whether the lookup will be focused
   */
  @Deprecated
  @NotNull
  public ThreeState shouldFocusLookup(@NotNull CompletionParameters parameters) { 
    return ThreeState.UNSURE; 
  }

  /**
   * This method is invoked first when a completion autopopup is scheduled. Extensions are able to cancel this completion process based on location.
   * For example, in string literals or comments completion autopopup may do more harm than good.
   */
  @NotNull
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    return ThreeState.UNSURE;
  }
}
