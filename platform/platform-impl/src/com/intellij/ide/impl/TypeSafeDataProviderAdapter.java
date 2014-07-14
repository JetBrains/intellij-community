/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  public synchronized Object getData(@NonNls String dataId) {
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
