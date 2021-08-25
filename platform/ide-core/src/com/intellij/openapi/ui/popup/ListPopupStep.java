// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Interface for a custom step in a list popup.
 *
 * @see ListPopup
 * @see JBPopupFactory#createListPopup(ListPopupStep)
 */
public interface ListPopupStep<T> extends PopupStep<T> {
  /**
   * Returns the values to be displayed in the list popup.
   */
  @NotNull List<T> getValues();

  /**
   * Checks if the specified value in the list can be selected.
   *
   * @param value the value to check.
   * @return true if the value can be selected, false otherwise.
   */
  boolean isSelectable(T value);

  default boolean isFinal(T value) {
    return !hasSubstep(value);
  }

  /**
   * Returns the icon to display for the specified list item.
   *
   * @param value the value for which the icon is requested.
   * @return the icon to display, or null if no icon is necessary.
   */
  @Nullable Icon getIconFor(T value);

  default Icon getSelectedIconFor(T value) {
    return getIconFor(value);
  }

  /**
   * Returns the text to display for the specified list item.
   *
   * @param value the value for which the text is requested.
   * @return the text to display.
   */
  @NlsContexts.ListItem @NotNull String getTextFor(T value);

  /**
   * Returns the separator to display above the specified list item.
   *
   * @param value the value for which the separator is requested.
   * @return the separator to display, or null if no separator is necessary.
   */
  @Nullable ListSeparator getSeparatorAbove(T value);

  /**
   * Returns the index of the item to be initially selected in the list.
   */
  int getDefaultOptionIndex();
}
