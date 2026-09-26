// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Aggregates 'generic' (language-agnostic) {@link ElementSignatureProvider signature providers}.
 * <p/>
 * Thread-safe.
 */
@ApiStatus.Internal
public final class GenericElementSignatureProvider implements ElementSignatureProvider {

  private static final ElementSignatureProvider[] PROVIDERS = {
    new PsiNamesElementSignatureProvider(), new OffsetsElementSignatureProvider()
  };

  @Override
  public String getSignature(@NotNull PsiElement element) {
    for (ElementSignatureProvider provider : PROVIDERS) {
      String result = provider.getSignature(element);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Override
  public PsiElement restoreBySignature(@NotNull PsiFile file, @NotNull String signature, @Nullable StringBuilder processingInfoStorage) {
    for (ElementSignatureProvider provider : PROVIDERS) {
      PsiElement result = provider.restoreBySignature(file, signature, processingInfoStorage);
      if (result != null) {
        return result;
      }
    }
    return null;
  }
}
