/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * A marker interface for the action which could be toggled between "selected" and "not selected" states.
 */
public interface Toggleable {
  /**
   * A property for the presentation to hold the state of the toggleable action.
   * Normally, you should not use this directly.
   * Use {@link #isSelected(Presentation)} and {@link #setSelected(Presentation, boolean)} methods instead.
   * @deprecated Use SELECTED_KEY instead
   */
  @ApiStatus.Internal
  @Deprecated
  @NonNls String SELECTED_PROPERTY = "selected";

  @ApiStatus.Internal
  Key<Boolean> SELECTED_KEY = Key.create("selected");

  /**
   * Checks whether given presentation is in the "selected" state
   * @param presentation presentation to check
   * @return true if "selected", false if "not selected" or the state wasn't set previously.
   */
  @Contract(pure = true)
  static boolean isSelected(@NotNull Presentation presentation) {
    return Boolean.TRUE.equals(presentation.getClientProperty(SELECTED_KEY));
  }

  /**
   * Sets the selected state for the given presentation (assuming it's a presentation of a toggleable action)
   * @param presentation presentation to update
   * @param selected whether the state should be "selected" or "not selected".
   */
  static void setSelected(@NotNull Presentation presentation, boolean selected) {
    presentation.putClientProperty(SELECTED_KEY, selected);
  }
}