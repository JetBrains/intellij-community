// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl;

import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import org.jetbrains.annotations.NotNull;

public class PsiParameterizedCachedValue<T,P> extends PsiCachedValue<T> implements ParameterizedCachedValue<T,P> {
  private final ParameterizedCachedValueProvider<T,P> myProvider;

  PsiParameterizedCachedValue(@NotNull PsiManager manager, @NotNull ParameterizedCachedValueProvider<T, P> provider, boolean trackValue) {
    super(manager, trackValue);
    myProvider = provider;
  }

  @Override
  public T getValue(P param) {
    return getValueWithLock(param);
  }

  @Override
  public @NotNull ParameterizedCachedValueProvider<T,P> getValueProvider() {
    return myProvider;
  }

  @Override
  protected <X> CachedValueProvider.Result<T> doCompute(X param) {
    return myProvider.compute((P)param);
  }
}
