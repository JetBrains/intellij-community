/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class DataManager {
  public static DataManager getInstance() {
    return ApplicationManager.getApplication().getComponent(DataManager.class);
  }

  public static final String CLIENT_PROPERTY_DATA_PROVIDER = "DataProvider";

  /**
   * @return {@link DataContext} constructed by the current focused component
   */
  public abstract DataContext getDataContext();

  /**
   * @return {@link DataContext} constructed by the specified <code>component</code>
   */
  public abstract DataContext getDataContext(Component component);

  /**
   * @return {@link DataContext} constructed be the specified <code>component</code>
   * and the point specified by <code>x</code> and <code>y</code> coordinate inside the
   * component.
   *
   * @exception java.lang.IllegalArgumentException if point <code>(x, y)</code> is not inside
   * component's bounds
   */
  public abstract DataContext getDataContext(@NotNull Component component, int x, int y);
}
