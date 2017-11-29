/*
 * Copyright 2000-2012 JetBrains s.r.o.
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


import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.awt.RelativePoint;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Base interface for popup windows.
 *
 * @author mike
 * @see com.intellij.openapi.ui.popup.JBPopupFactory
 * @since 6.0
 */
public interface JBPopup extends Disposable, LightweightWindow {

  @NonNls String KEY = "JBPopup";

  /**
   * Shows the popup at the bottom left corner of the specified component.
   *
   * @param componentUnder the component near which the popup should be displayed.
   */
  void showUnderneathOf(@NotNull Component componentUnder);

  /**
   * Shows the popup at the specified point.
   *
   * @param point the relative point where the popup should be displayed.
   */
  void show(@NotNull RelativePoint point);

  void showInScreenCoordinates(@NotNull Component owner, @NotNull Point point);

  /**
   * Shows the popup in the position most appropriate for the specified data context.
   *
   * @param dataContext the data context to which the popup is related.
   * @see com.intellij.openapi.ui.popup.JBPopupFactory#guessBestPopupLocation(com.intellij.openapi.actionSystem.DataContext)
   */
  void showInBestPositionFor(@NotNull DataContext dataContext);



  /**
   * Shows the popup near the cursor location in the specified editor.
   *
   * @param editor the editor relative to which the popup should be displayed.
   * @see com.intellij.openapi.ui.popup.JBPopupFactory#guessBestPopupLocation(com.intellij.openapi.editor.Editor)
   */
  void showInBestPositionFor(@NotNull Editor editor);

  /**
   * Shows the popup in the center of the specified component.
   *
   * @param component the component at which the popup should be centered.
   */
  void showInCenterOf(@NotNull Component component);


  /**
   * Shows the popups in the center of currently focused component
   */
  void showInFocusCenter();

  /**
   * Shows in best position with a given owner
   */
  void show(Component owner);  

  /**
   * Shows the popup in the center of the active window in the IDEA frame for the specified project.
   *
   * @param project the project in which the popup should be displayed.
   */
  void showCenteredInCurrentWindow(@NotNull Project project);

  /**
   * Hides popup as if Enter was pressed or or any other "accept" action
   */
  void closeOk(@Nullable InputEvent e);

  /**
   * Cancels the popup as if Esc was pressed or any other "cancel" action
   */
  void cancel();

  /**
   * @param b true if popup should request focus
   */
  void setRequestFocus(final boolean b);

  /**
   * Cancels the popup as a response to some mouse action. All the subsequent mouse events originated from the event's point
   * will be consumed.
   * @param e
   */
  void cancel(@Nullable InputEvent e);

  /**
   * Checks if it's currently allowed to close the popup.
   *
   * @return true if the popup can be closed, false if a callback disallowed closing the popup.
   * @see com.intellij.openapi.ui.popup.ComponentPopupBuilder#setCancelCallback(com.intellij.openapi.util.Computable)
   */
  boolean canClose();

  /**
   * Checks if the popup is currently visible.
   *
   * @return true if the popup is visible, false otherwise.
   */
  boolean isVisible();

  /**
   * Returns the Swing component contained in the popup.
   *
   * @return the contents of the popup.
   */
  JComponent getContent();

  /**
   * Moves popup to the given point. Does nothing if popup is invisible.
   * @param screenPoint Point to move to.
   */
  void setLocation(@NotNull Point screenPoint);

  void setSize(@NotNull Dimension size);
  Dimension getSize();

  boolean isPersistent();

  boolean isModalContext();

  boolean isNativePopup();
  void setUiVisible(boolean visible);

  @Nullable
    <T>
  T getUserData(Class<T> userDataClass);

  boolean isFocused();

  boolean isCancelKeyEnabled();

  void addListener(JBPopupListener listener);
  void removeListener(JBPopupListener listener);

  boolean isDisposed();

  Component getOwner();
  
  void setMinimumSize(Dimension size);

  void setFinalRunnable(@Nullable Runnable runnable);

  void moveToFitScreen();

  Point getLocationOnScreen();

  void pack(boolean width, boolean height);

  void setAdText(String s, @JdkConstants.HorizontalAlignment int alignment);

  void setDataProvider(@NotNull DataProvider dataProvider);

  /**
   * This callback is called when new key event from the event queue is being processed.
   * <p/>
   * The popup has a right to decide if its further processing should be continued (method return value).
   * 
   * @param e  new key event being processed
   * @return   {@code true} if the event is completely dispatched, i.e. no further processing is necessary;
   *           {@code false} otherwise
   */
  boolean dispatchKeyEvent(@NotNull KeyEvent e);
}
