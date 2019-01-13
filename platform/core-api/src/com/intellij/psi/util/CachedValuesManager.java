/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

/**
 * A service used to create and store {@link CachedValue} objects.<p/>
 *
 * By default cached values are stored in the user data of associated objects implementing {@link UserDataHolder}.
 *
 * @see #createCachedValue(CachedValueProvider, boolean)
 * @see #getCachedValue(PsiElement, CachedValueProvider)
 * @see #getCachedValue(UserDataHolder, CachedValueProvider)
 */
public abstract class CachedValuesManager {
  private static final NotNullLazyKey<CachedValuesManager, Project> INSTANCE_KEY = ServiceManager.createLazyKey(CachedValuesManager.class);

  public static CachedValuesManager getManager(@NotNull Project project) {
    return INSTANCE_KEY.getValue(project);
  }

  /**
   * Creates new CachedValue instance with given provider. If the return value is marked as trackable, it's treated as
   * yet another dependency and must comply its specification. See {@link CachedValueProvider.Result#getDependencyItems()} for
   * the details.
   *
   * @param provider computes values.
   * @param trackValue if value tracking is required. T should be trackable in this case.
   * @return new CachedValue instance.
   */
  @NotNull
  public abstract <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider, boolean trackValue);
  @NotNull
  public abstract <T,P> ParameterizedCachedValue<T,P> createParameterizedCachedValue(@NotNull ParameterizedCachedValueProvider<T,P> provider, boolean trackValue);

  /**
   * Rarely needed because it tracks the return value as a dependency.
   * @return a CachedValue like in {@link #createCachedValue(CachedValueProvider, boolean)}, with trackable return value.
   */
  @NotNull
  public <T> CachedValue<T> createCachedValue(@NotNull CachedValueProvider<T> provider) {
    return createCachedValue(provider, true);
  }

  public <T, P> T getParameterizedCachedValue(@NotNull UserDataHolder dataHolder,
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
      //noinspection SynchronizationOnLocalVariableOrMethodParameter
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
   * Utility method storing created cached values in a {@link UserDataHolder}.
   * The passed cached value provider may only depend on the passed user data holder and longer-living system state (e.g. project/application components/services),
   * see {@link CachedValue} documentation for more details.
   *
   * @param dataHolder holder to store the cached value, e.g. a PsiElement.
   * @param key key to store the cached value.
   * @param provider provider creating the cached value.
   * @param trackValue if value tracking is required (T should be trackable in that case). See {@link #createCachedValue(CachedValueProvider, boolean)} for more details.
   * @return up-to-date value.
   */
  public abstract <T> T getCachedValue(@NotNull UserDataHolder dataHolder,
                                       @NotNull Key<CachedValue<T>> key,
                                       @NotNull CachedValueProvider<T> provider,
                                       boolean trackValue);

  /**
   * Create a cached value with the given provider and non-tracked return value, store it in the first argument's user data. If it's already stored, reuse it.
   * The passed cached value provider may only depend on the passed user data holder and longer-living system state (e.g. project/application components/services),
   * see {@link CachedValue} documentation for more details.
   * @return The cached value
   */
  public <T> T getCachedValue(@NotNull UserDataHolder dataHolder, @NotNull CachedValueProvider<T> provider) {
    return getCachedValue(dataHolder, this.getKeyForClass(provider.getClass()), provider, false);
  }

  /**
   * Create a cached value with the given provider and non-tracked return value, store it in PSI element's user data. If it's already stored, reuse it.
   * The passed cached value provider may only depend on the passed PSI element and project/application components/services,
   * see {@link CachedValue} documentation for more details.
   * @return The cached value
   */
  public static <T> T getCachedValue(@NotNull final PsiElement psi, @NotNull final CachedValueProvider<T> provider) {
    return getCachedValue(psi, getKeyForClass(provider.getClass(), globalKeyForProvider), provider);
  }

  /**
   * Create a cached value with the given provider and non-tracked return value, store it in PSI element's user data. If it's already stored, reuse it.
   * The passed cached value provider may only depend on the passed PSI element and project/application components/services,
   * see {@link CachedValue} documentation for more details.
   * @return The cached value
   */
  public static <T> T getCachedValue(@NotNull final PsiElement psi, @NotNull Key<CachedValue<T>> key, @NotNull final CachedValueProvider<T> provider) {
    CachedValue<T> value = psi.getUserData(key);
    if (value != null) {
      return value.getValue();
    }

    return getManager(psi.getProject()).getCachedValue(psi, key, () -> {
      CachedValueProvider.Result<T> result = provider.compute();
      if (result != null && !psi.isPhysical()) {
        PsiFile file = psi.getContainingFile();
        if (file != null) {
          return CachedValueProvider.Result.create(result.getValue(), ArrayUtil.append(result.getDependencyItems(), file, ArrayUtil.OBJECT_ARRAY_FACTORY));
        }
      }
      return result;
    }, false);
  }

  private final ConcurrentMap<String, Key<CachedValue>> keyForProvider = ContainerUtil.newConcurrentMap();
  private static final ConcurrentMap<String, Key<CachedValue>> globalKeyForProvider = ContainerUtil.newConcurrentMap();

  @NotNull
  public <T> Key<CachedValue<T>> getKeyForClass(@NotNull Class<?> providerClass) {
    return getKeyForClass(providerClass, keyForProvider);
  }

  @NotNull
  private static <T> Key<CachedValue<T>> getKeyForClass(@NotNull Class<?> providerClass, ConcurrentMap<String, Key<CachedValue>> keyForProvider) {
    String name = providerClass.getName();
    assert name != null : providerClass + " doesn't have a name; can't be used for cache value provider";
    Key<CachedValue> key = keyForProvider.get(name);
    if (key == null) {
      key = ConcurrencyUtil.cacheOrGet(keyForProvider, name, Key.create(name));
    }
    //noinspection unchecked
    return (Key)key;
  }
}
