// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

public class JavaSmartCompletionParameters {
  private final CompletionParameters myParameters;
  private final ExpectedTypeInfo myExpectedType;

  public JavaSmartCompletionParameters(CompletionParameters parameters, final ExpectedTypeInfo expectedType) {
    myParameters = parameters;
    myExpectedType = expectedType;
  }

  public @NotNull PsiType getExpectedType() {
    return myExpectedType.getType();
  }

  public PsiType getDefaultType() {
    return myExpectedType.getDefaultType();
  }

  public PsiElement getPosition() {
    return myParameters.getPosition();
  }

  public CompletionParameters getParameters() {
    return myParameters;
  }
}
