// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.PopupAdvertisement;
import com.intellij.ui.awt.RelativePoint;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

/**
 * Base interface for popup windows.
 *
 * @author mike
 * @see JBPopupFactory
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
   * Returns location most appropriate for the specified data context.
   *
   * @see #showInBestPositionFor(DataContext)
   * @see #setLocation(Point)
   */
  @NotNull
  Point getBestPositionFor(@NotNull DataContext dataContext);

  /**
   * Shows the popup in the position most appropriate for the specified data context.
   *
   * @param dataContext the data context to which the popup is related.
   * @see JBPopupFactory#guessBestPopupLocation(DataContext)
   * @see #getBestPositionFor(DataContext)
   */
  void showInBestPositionFor(@NotNull DataContext dataContext);

  /**
   * Shows the popup near the cursor location in the specified editor.
   *
   * @param editor the editor relative to which the popup should be displayed.
   * @see JBPopupFactory#guessBestPopupLocation(Editor)
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
  void show(@NotNull Component owner);

  /**
   * Shows the popup in the center of the active window in the IDE frame for the specified project.
   *
   * @param project the project in which the popup should be displayed.
   */
  void showCenteredInCurrentWindow(@NotNull Project project);

  /**
   * Hides popup as if <kbd>Enter</kbd> was pressed or or any other "accept" action.
   */
  void closeOk(@Nullable InputEvent e);

  /**
   * Cancels the popup as if <kbd>Esc</kbd> was pressed or any other "cancel" action.
   */
  void cancel();

  /**
   * @param b {@code true} if popup should request focus.
   */
  void setRequestFocus(final boolean b);

  /**
   * Cancels the popup as a response to some mouse action. All the subsequent mouse events originated from the event's point
   * will be consumed.
   */
  void cancel(@Nullable InputEvent e);

  /**
   * Checks if it's currently allowed to close the popup.
   *
   * @return {@code true} if the popup can be closed, {@code false} if a callback disallowed closing the popup.
   * @see ComponentPopupBuilder#setCancelCallback(com.intellij.openapi.util.Computable)
   */
  boolean canClose();

  /**
   * Checks if the popup is currently visible.
   *
   * @return {@code true} if the popup is visible, {@code false} otherwise.
   */
  boolean isVisible();

  /**
   * Returns the Swing component contained in the popup.
   *
   * @return the contents of the popup.
   */
  @NotNull
  JComponent getContent();

  /**
   * Updates the popup location and size at once.
   *
   * @param bounds preferred popup location and size
   */
  default void setBounds(@NotNull Rectangle bounds) {
    setLocation(bounds.getLocation());
    setSize(bounds.getSize());
  }

  /**
   * Moves popup to the given point. Does nothing if popup is invisible.
   *
   * @param screenPoint Point to move to.
   */
  void setLocation(@NotNull Point screenPoint);

  void setSize(@NotNull Dimension size);

  Dimension getSize();

  void setCaption(@NotNull @NlsContexts.PopupTitle String title);

  boolean isPersistent();

  boolean isModalContext();

  boolean isNativePopup();

  void setUiVisible(boolean visible);

  default void setUserData(@NotNull List<Object> userData) {}

  @Nullable
  <T> T getUserData(@NotNull Class<T> userDataClass);

  boolean isFocused();

  boolean isCancelKeyEnabled();

  void addListener(@NotNull JBPopupListener listener);

  void removeListener(@NotNull JBPopupListener listener);

  boolean isDisposed();

  Component getOwner();

  void setMinimumSize(@Nullable Dimension size);

  void setFinalRunnable(@Nullable Runnable runnable);

  void moveToFitScreen();

  @NotNull
  Point getLocationOnScreen();

  void pack(boolean width, boolean height);

  void setAdText(@PopupAdvertisement String s, @JdkConstants.HorizontalAlignment int alignment);

  void setDataProvider(@NotNull DataProvider dataProvider);

  /**
   * This callback is called when new key event from the event queue is being processed.
   * <p/>
   * The popup has a right to decide if its further processing should be continued (method return value).
   *
   * @param e new key event being processed
   * @return {@code true} if the event is completely dispatched, i.e. no further processing is necessary;
   * {@code false} otherwise
   */
  boolean dispatchKeyEvent(@NotNull KeyEvent e);

  /**
   * Whether it's OK to invoke one of the 'show' methods. Some implementation might prohibit it e.g. if the popup is shown already.
   */
  default boolean canShow() { return !isDisposed(); }
}
