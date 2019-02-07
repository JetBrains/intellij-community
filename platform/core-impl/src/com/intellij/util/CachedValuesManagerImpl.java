// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class CachedValuesManagerImpl extends CachedValuesManager {

  private final Project myProject;
  private final CachedValuesFactory myFactory;

  public CachedValuesManagerImpl(Project project, CachedValuesFactory factory) {
    myProject = project;
    myFactory = factory == null ? new DefaultCachedValuesFactory(project) : factory;
  }

  @NotNull
  @Override
  public <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue) {
    return myFactory.createCachedValue(provider, trackValue);
  }

  @NotNull
  @Override
  public <T,P> ParameterizedCachedValue<T,P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T,P> provider, boolean trackValue) {
    return myFactory.createParameterizedCachedValue(provider, trackValue);
  }

  @Override
  @Nullable
  public <T> T getCachedValue(@NotNull UserDataHolder dataHolder,
                              @NotNull Key<CachedValue<T>> key,
                              @NotNull CachedValueProvider<T> provider,
                              boolean trackValue) {
    return dataHolder instanceof UserDataHolderEx
           ? getCachedValueFromExHolder((UserDataHolderEx)dataHolder, key, provider, trackValue)
           : getCachedValueFromHolder(dataHolder, key, provider, trackValue);
  }

  private <T> T getCachedValueFromExHolder(@NotNull UserDataHolderEx dataHolder,
                                           @NotNull Key<CachedValue<T>> key,
                                           @NotNull CachedValueProvider<T> provider,
                                           boolean trackValue) {
    CachedValue<T> value = dataHolder.getUserData(key);
    if (value instanceof CachedValueBase && ((CachedValueBase)value).isFromMyProject(myProject)) {
      //noinspection unchecked
      CachedValueBase.Data<T> data = ((CachedValueBase<T>)value).getUpToDateOrNull();
      if (data != null) {
        return data.getValue();
      }
    }
    while (isOutdated(value)) {
      if (dataHolder.replace(key, value, null)) {
        value = null;
        break;
      }
      value = dataHolder.getUserData(key);
    }
    if (value == null) {
      value = dataHolder.putUserDataIfAbsent(key, freshCachedValue(dataHolder, key, provider, trackValue));
    }
    return value.getValue();
  }

  private <T> T getCachedValueFromHolder(@NotNull UserDataHolder dataHolder,
                                         @NotNull Key<CachedValue<T>> key,
                                         @NotNull CachedValueProvider<T> provider, boolean trackValue) {
    CachedValue<T> value;
    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (dataHolder) {
      value = dataHolder.getUserData(key);
      if (isOutdated(value)) {
        value = null;
      }
      if (value == null) {
        value = freshCachedValue(dataHolder, key, provider, trackValue);
        dataHolder.putUserData(key, value);
      }
    }
    return value.getValue();
  }

  private <T> CachedValue<T> freshCachedValue(UserDataHolder dh, Key<CachedValue<T>> key, CachedValueProvider<T> provider, boolean trackValue) {
    CachedValueLeakChecker.checkProvider(provider, key, dh);
    CachedValue<T> value = createCachedValue(provider, trackValue);
    assert ((CachedValueBase)value).isFromMyProject(myProject);
    return value;
  }

  private boolean isOutdated(CachedValue<?> value) {
    return value instanceof CachedValueBase &&
           (!((CachedValueBase)value).isFromMyProject(myProject) || hasOutdatedValue((CachedValueBase)value));
  }

  private static boolean hasOutdatedValue(CachedValueBase base) {
    return !base.hasUpToDateValue() && base.getRawData() != null;
  }

}
