/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class DataManager {
  public static DataManager getInstance() {
    return ApplicationManager.getApplication().getComponent(DataManager.class);
  }

  @NonNls public static final String CLIENT_PROPERTY_DATA_PROVIDER = "DataProvider";

  /**
   * @return {@link DataContext} constructed by the current focused component
   * @deprecated use either {@link #getDataContext(Component)} or {@link #getDataContextFromFocus()}
   */
  @NotNull
  public abstract DataContext getDataContext();

  public abstract AsyncResult<DataContext> getDataContextFromFocus();

  /**
   * @return {@link DataContext} constructed by the specified <code>component</code>
   */
  public abstract DataContext getDataContext(Component component);

  /**
   * @return {@link DataContext} constructed be the specified <code>component</code>
   * and the point specified by <code>x</code> and <code>y</code> coordinate inside the
   * component.
   *
   * @exception IllegalArgumentException if point <code>(x, y)</code> is not inside
   * component's bounds
   */
  public abstract DataContext getDataContext(@NotNull Component component, int x, int y);

  /**
   * @param dataContext should be instance of {@link com.intellij.openapi.util.UserDataHolder}
   * @param dataKey key to store value
   * @param data value to store
   */
  public abstract <T> void saveInDataContext(@Nullable DataContext dataContext, @NotNull Key<T> dataKey, @Nullable T data);

  /**
   * @param dataContext find by key if instance of {@link com.intellij.openapi.util.UserDataHolder}
   * @param dataKey key to find value by
   * @return value stored by {@link #saveInDataContext(DataContext, Key, Object)}
   */
  @Nullable
  public abstract <T> T loadFromDataContext(@NotNull DataContext dataContext, @NotNull Key<T> dataKey);

  public static void registerDataProvider(@NotNull JComponent component, @NotNull DataProvider provider) {
    component.putClientProperty(CLIENT_PROPERTY_DATA_PROVIDER, provider);
  }

  @Nullable
  public static DataProvider getDataProvider(@NotNull JComponent component) {
    return (DataProvider)component.getClientProperty(CLIENT_PROPERTY_DATA_PROVIDER);
  }

  public static void removeDataProvider(@NotNull JComponent component) {
    component.putClientProperty(CLIENT_PROPERTY_DATA_PROVIDER, null);
  }
}
