/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 25.10.2006
 * Time: 17:24:41
 */
package com.intellij.ide.impl;

import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.TypeSafeDataProvider;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class TypeSafeDataProviderAdapter implements DataProvider, DataSink {
  private TypeSafeDataProvider myProvider;
  private DataKey myLastKey = null;
  private Object myValue = null;

  public TypeSafeDataProviderAdapter(final TypeSafeDataProvider provider) {
    myProvider = provider;
  }

  @Nullable
  synchronized public Object getData(@NonNls String dataId) {
    myValue = null;
    myLastKey = DataKey.create(dataId);
    myProvider.calcData(myLastKey, this);
    return myValue;
  }

  synchronized public <T> void put(DataKey<T> key, T data) {
    if (key == myLastKey) {
      myValue = data;
    }
  }
}