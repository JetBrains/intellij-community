// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.*;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * @author ven
 */
public final class CachedValuesManagerImpl extends CachedValuesManager {
  private static final Object NULL = new Object();
  private ConcurrentMap<UserDataHolder, Object> myCacheHolders = ContainerUtil.createConcurrentWeakMap(ContainerUtil.identityStrategy());
  private Set<Key<?>> myKeys = ContainerUtil.newConcurrentSet();

  private final Project myProject;
  private final CachedValuesFactory myFactory;

  public CachedValuesManagerImpl(Project project) {
    myProject = project;

    CachedValuesFactory factory = project.getService(CachedValuesFactory.class);
    myFactory = factory == null ? new DefaultCachedValuesFactory(project) : factory;
  }

  @NonInjectable
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
    CachedValue<T> value = dataHolder.getUserData(key);
    if (value instanceof CachedValueBase && ((CachedValueBase)value).isFromMyProject(myProject)) {
      Getter<T> data = value.getUpToDateOrNull();
      if (data != null) {
        return data.get();
      }

      CachedValueStabilityChecker.checkProvidersEquivalent(provider, value.getValueProvider(), key);
    }
    if (value == null) {
      value = saveInUserData(dataHolder, key, freshCachedValue(dataHolder, key, provider, trackValue));
    }
    return value.getValue();
  }

  private <T> CachedValue<T> saveInUserData(@NotNull UserDataHolder dataHolder,
                                            @NotNull Key<CachedValue<T>> key, CachedValue<T> value) {
    trackKeyHolder(dataHolder, key);

    if (dataHolder instanceof UserDataHolderEx) {
      return ((UserDataHolderEx)dataHolder).putUserDataIfAbsent(key, value);
    }

    //noinspection SynchronizationOnLocalVariableOrMethodParameter
    synchronized (dataHolder) {
      CachedValue<T> existing = dataHolder.getUserData(key);
      if (existing != null) {
        return existing;
      }
      dataHolder.putUserData(key, value);
      return value;
    }
  }

  @Override
  protected void trackKeyHolder(@NotNull UserDataHolder dataHolder,
                                @NotNull Key<?> key) {
    if (!isClearedOnPluginUnload(dataHolder)) {
      myCacheHolders.put(dataHolder, NULL);
      myKeys.add(key);
    }
  }

  private static boolean isClearedOnPluginUnload(@NotNull UserDataHolder dataHolder) {
    return dataHolder instanceof PsiElement || dataHolder instanceof ASTNode || dataHolder instanceof FileViewProvider;
  }

  private <T> CachedValue<T> freshCachedValue(UserDataHolder dh, Key<CachedValue<T>> key, CachedValueProvider<T> provider, boolean trackValue) {
    CachedValueLeakChecker.checkProvider(provider, key, dh);
    CachedValue<T> value = createCachedValue(provider, trackValue);
    assert ((CachedValueBase)value).isFromMyProject(myProject);
    return value;
  }

  @ApiStatus.Internal
  public void clearCachedValues() {
    for (UserDataHolder holder : myCacheHolders.keySet()) {
      for (Key<?> key : myKeys) {
        holder.putUserData(key, null);
      }
    }
    CachedValueStabilityChecker.cleanupFieldCache();
    myCacheHolders = ContainerUtil.createConcurrentWeakMap(ContainerUtil.identityStrategy());
    myKeys = ContainerUtil.newConcurrentSet();
  }
}
