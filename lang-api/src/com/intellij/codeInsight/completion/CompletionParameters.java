/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author peter
 */
public interface CompletionParameters {
  @NotNull
  PsiElement getPosition();

  @Nullable
  PsiElement getOriginalPosition();

  @NotNull
  PsiFile getOriginalFile();

  @NotNull
  CompletionType getCompletionType();

  int getOffset();
  
  int getInvocationCount();
}
