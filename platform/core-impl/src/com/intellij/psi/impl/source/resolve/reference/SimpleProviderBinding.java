// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class SimpleProviderBinding implements ProviderBinding {
  /**
   * the array must be copy-on-write to avoid data races, since it can be read concurrently, via {@link #addAcceptableReferenceProviders}
   */
  @SuppressWarnings("unchecked")
  private volatile @NotNull ProviderInfo<ElementPattern<?>> @NotNull [] myProviderInfos = (ProviderInfo<ElementPattern<?>>[])ProviderInfo.EMPTY_ARRAY;

  synchronized
  void registerProvider(@NotNull PsiReferenceProvider provider, @NotNull ElementPattern<?> pattern, double priority) {
    myProviderInfos = NamedObjectProviderBinding.appendToArray(myProviderInfos, new ProviderInfo<>(provider, pattern, priority));
  }

  @Override
  public void addAcceptableReferenceProviders(@NotNull PsiElement position,
                                              @NotNull List<? super ProviderInfo<ProcessingContext>> list,
                                              @NotNull PsiReferenceService.Hints hints) {
    NamedObjectProviderBinding.addMatchingProviders(position, myProviderInfos, list, hints);
  }

  @Override
  public synchronized void unregisterProvider(@NotNull PsiReferenceProvider provider) {
    myProviderInfos = NamedObjectProviderBinding.removeFromArray(provider, myProviderInfos);
  }

  boolean isEmpty() {
    return myProviderInfos.length == 0;
  }
}
