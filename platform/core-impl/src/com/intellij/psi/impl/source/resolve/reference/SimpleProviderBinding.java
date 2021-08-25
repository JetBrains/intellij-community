// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceService;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class SimpleProviderBinding implements ProviderBinding {
  private final List<ProviderInfo<ElementPattern<?>>> myProviderPairs = ContainerUtil.createLockFreeCopyOnWriteList();

  void registerProvider(@NotNull PsiReferenceProvider provider, @NotNull ElementPattern<?> pattern, double priority) {
    myProviderPairs.add(new ProviderInfo<>(provider, pattern, priority));
  }

  @Override
  public void addAcceptableReferenceProviders(@NotNull PsiElement position,
                                              @NotNull List<? super ProviderInfo<ProcessingContext>> list,
                                              @NotNull PsiReferenceService.Hints hints) {
    NamedObjectProviderBinding.addMatchingProviders(position, myProviderPairs, list, hints);
  }

  @Override
  public void unregisterProvider(@NotNull PsiReferenceProvider provider) {
    myProviderPairs.removeIf(trinity -> trinity.provider.equals(provider));
  }

  boolean isEmpty() {
    return myProviderPairs.isEmpty();
  }
}
