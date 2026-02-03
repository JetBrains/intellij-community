// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A {@link ComboBox}'s model may implement
 * that interface to handle a custom sub-popup to be shown for some
 * ComboBox elements.
 * <br />
 * All elements of the ComboBox and it's sub-popups has to have the
 * same element type to be able to re-use the ComboBox configured
 * presenter
 * <br />
 * Note, it is necessary to call {@code com.intellij.openapi.ui.ComboBox#setSwingPopup(false)}
 * before to activate the checks and to allow JBPopup to be used
 *
 * @see com.intellij.ide.ui.laf.darcula.ui.DarculaJBPopupComboPopup
 */
@ApiStatus.Experimental
public interface ComboBoxPopupState<T> {
  /**
   * This method is called on the {@link ComboBoxModel} instance of the
   * combobox to decide if there is next step needed or not.
   * <br/>
   * Return {@code null} if there is no sup-popup to be shown. That would
   * mean the {@link ComboBox#setSelectedItem(Object)}
   * is executed with the same {@param selectedValue} parameter.
   * <br/>
   * A non-null value would mean a sub-popup to be created. The sub-popup
   * will use the same element type and the same renderer as the original
   * {@link ComboBox}. The returned object will
   * be checked again to implement this interface.
   *
   * @param selectedValue the selected item.
   * @return new model to show sub-popup or {@code null} to accept the suggested element.
   */
  @Nullable
  ListModel<T> onChosen(T selectedValue);

  /**
   * In addition to the {@link #onChosen(Object)} is it possible to
   * show the sub-popup on a mouse hover, without an explicit click.
   * @return {@code true} to allow that.
   */
  boolean hasSubstep(T selectedValue);
}
