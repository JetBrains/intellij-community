// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public final class PsiCachedValueImpl<T> extends PsiCachedValue<T> implements CachedValue<T>  {
  private final CachedValueProvider<T> myProvider;

  public PsiCachedValueImpl(@NotNull PsiManager manager, @NotNull CachedValueProvider<T> provider) {
    this(manager, provider, false);
  }

  PsiCachedValueImpl(@NotNull PsiManager manager, @NotNull CachedValueProvider<T> provider, boolean trackValue) {
    super(manager, trackValue);
    myProvider = provider;
  }

  @Override
  @Nullable
  public T getValue() {
    return getValueWithLock(null);
  }

  @NotNull
  @Override
  public CachedValueProvider<T> getValueProvider() {
    return myProvider;
  }

  @Override
  protected <P> CachedValueProvider.Result<T> doCompute(P param) {
    return myProvider.compute();
  }
}
