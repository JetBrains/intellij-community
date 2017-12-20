/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.ui.popup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Interface for a custom step in a list popup.
 *
 * @see ListPopup
 * @see JBPopupFactory#createListPopup(ListPopupStep)
 * @since 6.0
 */
public interface ListPopupStep<T> extends PopupStep<T> {

  /**
   * Returns the values to be displayed in the list popup.
   *
   * @return the list of values to be displayed in the list popup.
   */
  @NotNull
  List<T> getValues();

  /**
   * Checks if the specified value in the list can be selected.
   *
   * @param value the value to check.
   * @return true if the value can be selected, false otherwise.
   */
  boolean isSelectable(T value);

  /**
   * Returns the icon to display for the specified list item.
   *
   * @param aValue the value for which the icon is requested.
   * @return the icon to display, or null if no icon is necessary.
   */
  @Nullable
  Icon getIconFor(T aValue);

  default Icon getSelectedIconFor(T value) {
    return getIconFor(value);
  }

  /**
   * Returns the text to display for the specified list item.
   *
   * @param value the value for which the text is requested.
   * @return the text to display.
   */
  @NotNull
  String getTextFor(T value);

  /**
   * Returns the separator to display above the specified list item.
   *
   * @param value the value for which the separator is requested.
   * @return the separator to display, or null if no separator is necessary.
   */
  @Nullable
  ListSeparator getSeparatorAbove(T value);

  /**
   * Returns the index of the item to be initially selected in the list.
   *
   * @return the index of the item to be initially selected in the list.
   */
  int getDefaultOptionIndex();
}
