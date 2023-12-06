// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ProviderBinding {
  class ProviderInfo<Context> {
    public final @NotNull PsiReferenceProvider provider;
    public final @NotNull Context processingContext;
    public final double priority;

    public ProviderInfo(@NotNull PsiReferenceProvider provider, @NotNull Context processingContext, double priority) {
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
