// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class PrepareFailedException extends Exception {
  private final PsiFile myContainingFile;
  private final TextRange myTextRange;

  public PrepareFailedException(@NotNull @NlsContexts.DialogMessage String message, @NotNull PsiElement errorElement) {
    super(message);
    myContainingFile = errorElement.getContainingFile();
    myTextRange = errorElement.getTextRange();
  }

  @Override
  public @NotNull @NlsContexts.DialogMessage String getMessage() {
    //noinspection HardCodedStringLiteral
    return super.getMessage();
  }

  public PsiFile getFile() {
    return myContainingFile;
  }

  public TextRange getTextRange() {
    return myTextRange;
  }
}
