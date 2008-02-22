/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author peter
 */
public interface CompletionParameters {
  @NotNull
  PsiElement getPosition();

  @NotNull
  PsiElement getOriginalPosition();

  @NotNull
  PsiFile getOriginalFile();

  @NotNull
  CompletionType getCompletionType();
}
