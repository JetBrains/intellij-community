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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// We implement UserDataHolder to support DataManager.saveInDataContext/loadFromDataContext methods
public class DataContextWrapper extends CustomizedDataContext implements UserDataHolder {
  private final DataContext myDelegate;
  private final UserDataHolder myDataHolder;

  public DataContextWrapper(@NotNull DataContext delegate) {
    myDelegate = delegate;
    myDataHolder = delegate instanceof UserDataHolder ? (UserDataHolder) delegate : new UserDataHolderBase();
  }

  @Override
  public final @NotNull DataContext getParent() {
    return myDelegate;
  }

  @Override
  @ApiStatus.NonExtendable
  public @Nullable Object getData(@NotNull String dataId) {
    return super.getData(dataId);
  }

  @Override
  public @Nullable Object getRawCustomData(@NotNull String dataId) {
    return null;
  }

  @Override
  public final @Nullable <T> T getUserData(@NotNull Key<T> key) {
    return myDataHolder.getUserData(key);
  }

  @Override
  public final <T> void putUserData(@NotNull Key<T> key, @Nullable T value) {
    myDataHolder.putUserData(key, value);
  }
}
