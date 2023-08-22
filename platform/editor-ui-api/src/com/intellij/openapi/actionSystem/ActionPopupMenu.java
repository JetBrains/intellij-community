/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.function.Supplier;

/**
 * Represents a popup menu with a visual presentation.
 *
 * @see ActionManager#createActionPopupMenu(String, ActionGroup)
 */
public interface ActionPopupMenu {
  /**
   * Returns the visual presentation of the popup menu.
   */
  @NotNull
  JPopupMenu getComponent();

  /**
   * Returns the place where the action group is displayed (the first parameter of {@link ActionManager#createActionPopupMenu(String, ActionGroup)}.
   */
  @NotNull
  @NonNls
  String getPlace();

  /**
   * Returns the action group from which the menu was created.
   */
  @NotNull
  ActionGroup getActionGroup();

  /**
   * Will be used for data-context retrieval.
   */
  void setTargetComponent(@NotNull JComponent component);

  /**
   * Will be used for data-context retrieval.
   */
  void setDataContext(@NotNull Supplier<? extends DataContext> dataProvider);
}
