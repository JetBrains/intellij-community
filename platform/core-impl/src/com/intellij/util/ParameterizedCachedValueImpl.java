// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util;

import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;

import static com.intellij.reference.SoftReference.dereference;

@ApiStatus.Internal
public final class ParameterizedCachedValueImpl<T,P> extends CachedValueBase<T> implements ParameterizedCachedValue<T,P> {
  private final @NotNull Project myProject;
  private final ParameterizedCachedValueProvider<T,P> myProvider;
  private volatile SoftReference<Data<T>> myData;
  private final boolean myTrackValue;

  ParameterizedCachedValueImpl(@NotNull Project project,
                               @NotNull ParameterizedCachedValueProvider<T, P> provider,
                               boolean trackValue) {
    myTrackValue = trackValue;
    myProject = project;
    myProvider = provider;
  }

  @Override
  protected boolean isTrackValue() {
    return myTrackValue;
  }

  @Override
  protected @Nullable Data<T> getRawData() {
    return dereference(myData);
  }

  @Override
  protected void setData(@Nullable Data<T> data) {
    myData = data == null ? null : new SoftReference<>(data);
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
