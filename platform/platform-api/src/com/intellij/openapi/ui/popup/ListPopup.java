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

import javax.swing.event.ListSelectionListener;
import java.awt.event.InputEvent;

/**
 * A popup window displaying a list of items (or other actions).
 *
 * @author mike
 * @see com.intellij.openapi.ui.popup.JBPopupFactory#createActionGroupPopup
 * @see com.intellij.openapi.ui.popup.JBPopupFactory#createWizardStep
 * @since 6.0
 */
public interface ListPopup extends JBPopup {

  /**
   * Returns the popup step currently displayed in the popup.
   *
   * @return the popup step.
   */
  ListPopupStep getListStep();

  /**
   * Handles the selection of the currently focused item in the popup step.
   *
   * @param handleFinalChoices If true, the action of the focused item is always executed
   * (as if Enter was pressed). If false, and the focused item has a submenu, the submenu
   * is opened (as if the right arrow key was pressed). 
   */
  void handleSelect(boolean handleFinalChoices);

  void handleSelect(boolean handleFinalChoices, InputEvent e);

  /**
   * If default selection is set, then handleSelect is invoked without showing a popup
   * @param autoHandle
   */
  void setHandleAutoSelectionBeforeShow(boolean autoHandle);

  void addListSelectionListener(ListSelectionListener listSelectionListener);
}
