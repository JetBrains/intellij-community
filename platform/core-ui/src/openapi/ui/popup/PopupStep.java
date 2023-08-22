// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

/**
 * Base interface for a single step (submenu) of a popup, displaying a list of items or a single level of a tree structure.
 *
 * @see ListPopupStep
 * @see TreePopupStep
 * @param <T> the type of the objects contained in the list or tree structure.
 */
public interface PopupStep<T> {
  PopupStep<?> FINAL_CHOICE = null;

  /**
   * Returns the title of list.
   */
  @NlsContexts.PopupTitle @Nullable  String getTitle();

  /**
   * Handles the selection of an item in the list.
   *
   * @param selectedValue the selected item.
   * @param finalChoice If true, the action associated with the selected item should be displayed. If false and the selected item has
   * a submenu, the popup step for the submenu should be returned.
   * @return the substep to be displayed, or {@link #FINAL_CHOICE} if the popup should be closed after the item has been selected.
   * @see #hasSubstep
   */
  @Nullable PopupStep<?> onChosen(T selectedValue, boolean finalChoice);

  /**
   * Checks if the specified item in the list has an associated substep.
   *
   * @param selectedValue the value to check for substep presence.
   * @return true if the value has a substep, false otherwise.
   */
  boolean hasSubstep(T selectedValue);

  /**
   * Called when the popup showing this step is canceled.
   */
  void canceled();

  /**
   * Returns true if items in the list can be selected by pressing a mnemonic character. If this method returns true,
   * {@link #isSpeedSearchEnabled()} must return false.
   *
   * @return true if navigation by mnemonics is enabled, false otherwise.
   * @see #getMnemonicNavigationFilter()
   */
  boolean isMnemonicsNavigationEnabled();

  /**
   * Returns the class supporting navigation by mnemonics in a popup.
   *
   * @return the mnemonics navigation filter instance, or null if navigation by mnemonics is not supported.
   * @see #isMnemonicsNavigationEnabled()
   */
  @Nullable MnemonicNavigationFilter<T> getMnemonicNavigationFilter();

  /**
   * Returns true if items in the list can be selected by typing part of an item's text. If this method returns true,
   * {@link #isMnemonicsNavigationEnabled()} must return false.
   *
   * @return true if speed search is enabled, false otherwise.
   */
  boolean isSpeedSearchEnabled();

  /**
   * Returns the class supporting speed search in a popup.
   *
   * @return the speed search filter instance, or null if speed search is not supported.
   * @see #isSpeedSearchEnabled()
   */
  @Nullable SpeedSearchFilter<T> getSpeedSearchFilter();

  /**
   * Returns true if the submenu for the first selectable item should be displayed automatically when the item has a submenu.
   *
   * @return true if the submenu for the first selectable item should be displayed automatically, false otherwise.
   */
  boolean isAutoSelectionEnabled();

  /**
   * @return runnable to be executed when final step is chosen
   */
  @Nullable Runnable getFinalRunnable();
}
