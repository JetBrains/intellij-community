// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import org.jetbrains.annotations.NotNull;

public class CachedValueImpl<T> extends CachedValueBase<T> implements CachedValue<T> {
  private final CachedValueProvider<T> myProvider;

  public CachedValueImpl(@NotNull CachedValueProvider<T> provider) {
    this(provider, false);
  }
  CachedValueImpl(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    super(trackValue);
    myProvider = provider;
  }

  @Override
  protected <P> CachedValueProvider.Result<T> doCompute(P param) {
    return myProvider.compute();
  }

  @NotNull
  @Override
  public CachedValueProvider<T> getValueProvider() {
    return myProvider;
  }

  @Override
  public T getValue() {
    return getValueWithLock(null);
  }

  @Override
  public boolean isFromMyProject(@NotNull Project project) {
    return true;
  }
}
