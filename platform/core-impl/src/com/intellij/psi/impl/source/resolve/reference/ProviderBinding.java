// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Internal
public interface ProviderBinding {
  class ProviderInfo<T> {
    static final ProviderInfo<?>[] EMPTY_ARRAY = new ProviderInfo[0];
    static final ArrayFactory<ProviderInfo<?>> ARRAY_FACTORY = n -> n == 0 ? ProviderInfo.EMPTY_ARRAY : new ProviderInfo[n];
    final @NotNull PsiReferenceProvider provider;
    final @NotNull T processingContext;
    final double priority;

    ProviderInfo(@NotNull PsiReferenceProvider provider, @NotNull T processingContext, double priority) {
      this.provider = provider;
      this.processingContext = processingContext;
      this.priority = priority;
    }

    @Override
    public String toString() {
      return "ProviderInfo{provider=" + provider + ", priority=" + priority + '}';
    }
  }
  void addAcceptableReferenceProviders(@NotNull PsiElement position,
                                       @NotNull List<? super ProviderInfo<ProcessingContext>> list,
                                       @NotNull PsiReferenceService.Hints hints);

  void unregisterProvider(@NotNull PsiReferenceProvider provider);
}
