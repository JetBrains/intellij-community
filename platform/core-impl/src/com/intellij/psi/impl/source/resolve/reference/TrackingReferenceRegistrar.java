// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.Disposable;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;

class TrackingReferenceRegistrar extends PsiReferenceRegistrar {
  private final PsiReferenceRegistrarImpl myDelegate;
  private final Disposable myDisposable;

  TrackingReferenceRegistrar(@NotNull PsiReferenceRegistrarImpl delegate, @NotNull Disposable disposable) {
    myDelegate = delegate;
    myDisposable = disposable;
  }

  @Override
  public <T extends PsiElement> void registerReferenceProvider(@NotNull ElementPattern<T> pattern,
                                                               @NotNull PsiReferenceProvider provider,
                                                               double priority) {
    myDelegate.registerReferenceProvider(pattern, provider, priority, myDisposable);
  }
}
