/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
 * Date: 23.10.2006
 * Time: 17:00:08
 */
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
