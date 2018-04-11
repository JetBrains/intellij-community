// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @param <T>
 * @see com.intellij.openapi.actionSystem.PlatformDataKeys
 */
public class DataKey<T> {
  private static final Map<String, DataKey> ourDataKeyIndex = new HashMap<>();

  private final String myName;

  private DataKey(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public static <T> DataKey<T> create(@NotNull @NonNls String name) {
    //noinspection unchecked
    DataKey<T> key = ourDataKeyIndex.get(name);
    if (key != null) {
      return key;
    }
    key = new DataKey<>(name);
    ourDataKeyIndex.put(name, key);
    return key;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  /**
   * For short, use MY_KEY.is(dataId) instead of MY_KEY.getName().equals(dataId)
   *
   * @param dataId key name
   * @return {@code true} if name of DataKey equals to {@code dataId},
   *         {@code false} otherwise
   */
  public final boolean is(String dataId) {
    return myName.equals(dataId);
  }

  @Nullable
  public T getData(@NotNull DataContext dataContext) {
    //noinspection unchecked
    return (T) dataContext.getData(myName);
  }

  @Nullable
  public T getData(@NotNull DataProvider dataProvider) {
    //noinspection unchecked
    return (T) dataProvider.getData(myName);
  }
}
