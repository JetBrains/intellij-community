// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public class PsiReferenceProcessorAdapter extends ReadActionProcessor<PsiReference> { // todo: drop
  private final @NotNull PsiReferenceProcessor myProcessor;

  public PsiReferenceProcessorAdapter(@NotNull PsiReferenceProcessor processor) {
    myProcessor = processor;
  }

  @Override
  public boolean processInReadAction(final PsiReference psiReference) {
    return myProcessor.execute(psiReference);
  }
}