// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.ValueKey;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Type-safe named key.
 * <p/>
 * Mainly used via {@link AnActionEvent#getData(DataKey)} calls and {@link DataProvider#getData(String)} implementations.
 * <p/>
 * Corresponding data for given {@code name} is provided by {@link DataProvider} implementations.
 * Globally available data can be provided via {@link com.intellij.ide.impl.dataRules.GetDataRule} extension point.
 *
 * @param <T> Data type.
 * @see CommonDataKeys
 * @see com.intellij.openapi.actionSystem.PlatformDataKeys
 * @see LangDataKeys
 */
public final class DataKey<T> implements ValueKey<T> {
  private static final ConcurrentMap<String, DataKey> ourDataKeyIndex = new ConcurrentHashMap<>();

  private final String myName;

  private DataKey(@NotNull String name) {
    myName = name;
  }

  public static @NotNull <T> DataKey<T> create(@NotNull @NonNls String name) {
    //noinspection unchecked
    return ourDataKeyIndex.computeIfAbsent(name, DataKey::new);
  }

  @Override
  public @NotNull String getName() {
    return myName;
  }

  /**
   * For short notation, use {@code MY_KEY.is(dataId)} instead of {@code MY_KEY.getName().equals(dataId)}.
   *
   * @param dataId key name
   * @return {@code true} if name of DataKey equals to {@code dataId}, {@code false} otherwise
   */
  public final boolean is(String dataId) {
    return myName.equals(dataId);
  }

  public @Nullable T getData(@NotNull DataContext dataContext) {
    //noinspection unchecked
    return (T)dataContext.getData(myName);
  }

  public @Nullable T getData(@NotNull DataProvider dataProvider) {
    //noinspection unchecked
    return (T)dataProvider.getData(myName);
  }
}
