/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.util;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NotNullLazyKey;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class CachedValuesManager {
  private static final NotNullLazyKey<CachedValuesManager, Project> INSTANCE_KEY = ServiceManager.createLazyKey(CachedValuesManager.class);

  public static CachedValuesManager getManager(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  /**
   * Creates new CachedValue instance with given provider.
   *
   * @param provider computes values.
   * @param trackValue if value tracking required. T should be trackable in this case.
   * @return new CachedValue instance.
   */
  public abstract <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue);
  public abstract <T,P> ParameterizedCachedValue<T,P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T,P> provider, boolean trackValue);

  public <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider) {
    return createCachedValue(provider, true);
  }

  public <T, D extends UserDataHolder, P> T getParameterizedCachedValue(@NotNull D dataHolder,
                              @NotNull Key<ParameterizedCachedValue<T,P>> key,
                              @NotNull ParameterizedCachedValueProvider<T, P> provider,
                              boolean trackValue,
                              P parameter) {

    ParameterizedCachedValue<T,P> value;

    if (dataHolder instanceof UserDataHolderEx) {
      UserDataHolderEx dh = (UserDataHolderEx)dataHolder;
      value = dh.getUserData(key);
      if (value == null) {
        value = createParameterizedCachedValue(provider, trackValue);
        value = dh.putUserDataIfAbsent(key, value);
      }
    }
    else {
      synchronized (dataHolder) {
        value = dataHolder.getUserData(key);
        if (value == null) {
          value = createParameterizedCachedValue(provider, trackValue);
          dataHolder.putUserData(key, value);
        }
      }
    }
    return value.getValue(parameter);
  }

  /**
   * Utility method storing created cached values in a {@link com.intellij.openapi.util.UserDataHolder}.
   *
   * @param dataHolder holder to store the cached value, e.g. a PsiElement.
   * @param key key to store the cached value.
   * @param provider provider creating the cached value.
   * @param trackValue if value tracking required. T should be trackable in this case.
   * @return up-to-date value.
   */
  public abstract <T, D extends UserDataHolder> T getCachedValue(@NotNull D dataHolder,
                              @NotNull Key<CachedValue<T>> key,
                              @NotNull CachedValueProvider<T> provider,
                              boolean trackValue);

  public <T, D extends UserDataHolder> T getCachedValue(@NotNull D dataHolder,
                              @NotNull CachedValueProvider<T> provider) {
    return getCachedValue(dataHolder, new MemoizationKey<CachedValue<T>>(provider.getClass().getName()), provider, false);
  }

  public static class MemoizationKey<T> extends Key<T> {
    public MemoizationKey(@NotNull @NonNls String name) {
      super(name);
    }

    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof MemoizationKey && toString().equals(obj.toString());
    }
  }

}
