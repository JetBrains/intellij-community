// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.impl;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TypeSafeDataProviderAdapter implements DataProvider, DataSink {
  private final TypeSafeDataProvider myProvider;
  private DataKey myLastKey = null;
  private Object myValue = null;

  public TypeSafeDataProviderAdapter(@NotNull TypeSafeDataProvider provider) {
    myProvider = provider;
  }

  @Override
  @Nullable
  public synchronized Object getData(@NotNull @NonNls String dataId) {
    myValue = null;
    myLastKey = DataKey.create(dataId);
    myProvider.calcData(myLastKey, this);
    return myValue;
  }

  @Override
  public synchronized <T> void put(DataKey<T> key, T data) {
    if (key == myLastKey) {
      myValue = data;
    }
  }

  @Override
  public String toString() {
    return super.toString()+'('+ myProvider + ')';
  }
}
