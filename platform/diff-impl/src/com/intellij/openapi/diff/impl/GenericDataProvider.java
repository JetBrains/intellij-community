// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

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

  public void putData(String key, Object value) {
    myGenericData.put(key, value);
  }

  @Override
  public Object getData(@NotNull String dataId) {
    Object data = myGenericData.get(dataId);
    if (data != null) return data;
    return myParentProvider != null ? myParentProvider.getData(dataId) : null;
  }
}
