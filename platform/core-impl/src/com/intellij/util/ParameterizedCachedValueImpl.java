// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import org.jetbrains.annotations.NotNull;

public final class ParameterizedCachedValueImpl<T,P> extends CachedValueBase<T> implements ParameterizedCachedValue<T,P> {
  private final @NotNull Project myProject;
  private final ParameterizedCachedValueProvider<T,P> myProvider;

  ParameterizedCachedValueImpl(@NotNull Project project,
                               @NotNull ParameterizedCachedValueProvider<T, P> provider,
                               boolean trackValue) {
    super(trackValue);
    myProject = project;
    myProvider = provider;
  }

  @Override
  public T getValue(P param) {
    return getValueWithLock(param);
  }

  @Override
  public boolean isFromMyProject(@NotNull Project project) {
    return myProject == project;
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
