// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated Prefer using {@link com.intellij.diff.util.DiffUtil#putDataKey} instead
 */
@ApiStatus.NonExtendable
@Deprecated
public class GenericDataProvider implements DataProvider {
  private final Map<String, Object> myGenericData;
  private final DataProvider myParentProvider;

  public GenericDataProvider() {
    this(null);
  }

  public GenericDataProvider(@Nullable DataProvider provider) {
    myParentProvider = provider;
    myGenericData = new HashMap<>();
  }

  public <T> void putData(DataKey<T> key, T value) {
    myGenericData.put(key.getName(), value);
  }

  @Override
  public Object getData(@NotNull String dataId) {
    Object data = myGenericData.get(dataId);
    if (data != null) return data;
    return myParentProvider != null ? myParentProvider.getData(dataId) : null;
  }
}
