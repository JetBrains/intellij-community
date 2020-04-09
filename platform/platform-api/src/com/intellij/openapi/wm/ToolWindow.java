// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Key;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerListener;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;

/**
 * Tool windows expose UI for specific functionality, like "Project" or "Favorites".
 *
 * @see ToolWindowEP
 */
public interface ToolWindow extends BusyObject {
  Key<Boolean> SHOW_CONTENT_ICON = new Key<>("ContentIcon");

  @NonNls
  @NotNull
  String getId();

  /**
   * @throws IllegalStateException if tool window isn't installed.
   */
  boolean isActive();

  /**
   * @param runnable A command to execute right after the window gets activated. The call is asynchronous since it may require animation.
   * @throws IllegalStateException if tool window isn't installed.
   */
  void activate(@Nullable Runnable runnable);

  void activate(@Nullable Runnable runnable, boolean autoFocusContents);

  void activate(@Nullable Runnable runnable, boolean autoFocusContents, boolean forced);

  /**
   * @return whether the tool window is visible or not.
   * @throws IllegalStateException if tool window isn't installed.
   */
  boolean isVisible();

  /**
   * @param runnable A command to execute right after the window shows up. The call is asynchronous since it may require animation.
   * @throws IllegalStateException if tool window isn't installed.
   */
  void show(@Nullable Runnable runnable);

  default void show() {
    show(null);
  }

  /**
   * Hides tool window. If the window is active, then the method deactivates it.
   * Does nothing if tool window isn't visible.
   *
   * @param runnable A command to execute right after the window hides. The call is asynchronous since it may require animation.
   * @throws IllegalStateException if tool window isn't installed.
   */
  void hide(@Nullable Runnable runnable);

  default void hide() {
    hide(null);
  }

  /**
   * @throws IllegalStateException if tool window isn't installed.
   */
  @NotNull
  ToolWindowAnchor getAnchor();

  /**
   * @throws IllegalStateException if tool window isn't installed.
   */
  void setAnchor(@NotNull ToolWindowAnchor anchor, @Nullable Runnable runnable);

  /**
   * @throws IllegalStateException if tool window isn't installed.
   */
  boolean isSplitMode();

  /**
   * There are four base {@link ToolWindowAnchor anchors} for Tool Window: TOP, LEFT, BOTTOM, RIGHT.
   * For each anchor there are two groups tool windows - not split and split for better organizing.
   * For example, you can see two actions in Move To group: Left Top and Left Bottom.
   * 'Left' here is anchor or side where the button is located,
   * 'Top' and 'Bottom' are two subsets of buttons (not split and split).
   *
   * @throws IllegalStateException if tool window isn't installed.
   * @see ToolWindowAnchor
   */
  void setSplitMode(boolean split, @Nullable Runnable runnable);

  /**
   * @throws IllegalStateException if tool window isn't installed.
   */
  boolean isAutoHide();

  void setAutoHide(boolean value);

  /**
   * @throws IllegalStateException if tool window isn't installed.
   */
  @NotNull
  ToolWindowType getType();

  /**
   * @throws IllegalStateException if tool window isn't installed.
   */
  void setType(@NotNull ToolWindowType type, @Nullable Runnable runnable);

  /**
   * @return Window icon. Returns {@code null} if window has no icon.
   */
  @Nullable
  Icon getIcon();

  /**
   * Sets new window icon.
   */
  void setIcon(@NotNull Icon icon);

  /**
   * @return Window title. Returns {@code null} if window has no title.
   */
  @Nullable
  String getTitle();

  /**
   * Sets new window title.
   */
  void setTitle(String title);

  /**
   * @return Window stripe button text.
   */
  @NotNull
  String getStripeTitle();

  /**
   * Sets new window stripe button text.
   */
  void setStripeTitle(@NotNull String title);

  /**
   * @return Whether the window is available or not.
   */
  boolean isAvailable();

  /**
   * Sets whether the tool window available or not. Term "available" means that tool window
   * can be shown and it has button on tool window bar.
   *
   * @throws IllegalStateException if tool window isn't installed.
   */
  void setAvailable(boolean value, @Nullable Runnable runnable);

  void setAvailable(boolean value);

  void setContentUiType(@NotNull ToolWindowContentUiType type, @Nullable Runnable runnable);

  void setDefaultContentUiType(@NotNull ToolWindowContentUiType type);

  @NotNull
  ToolWindowContentUiType getContentUiType();

  void installWatcher(ContentManager contentManager);

  /**
   * @return component which represents window content.
   */
  @NotNull
  JComponent getComponent();

  @NotNull
  ContentManager getContentManager();

  @Nullable
  ContentManager getContentManagerIfCreated();

  void addContentManagerListener(@NotNull ContentManagerListener listener);

  void setDefaultState(@Nullable ToolWindowAnchor anchor, @Nullable ToolWindowType type, @Nullable Rectangle floatingBounds);

  void setToHideOnEmptyContent(boolean hideOnEmpty);

  /**
   * @param value if {@code false} stripe button should be hidden.
   */
  void setShowStripeButton(boolean value);

  boolean isShowStripeButton();

  boolean isDisposed();

  void showContentPopup(@NotNull InputEvent inputEvent);

  @NotNull
  Disposable getDisposable();

  default void setHelpId(@NotNull @NonNls String helpId) {
  }

  default String getHelpId() {
    return null;
  }

  /**
   * Delete tool window.
   */
  void remove();

  /**
   * @deprecated Not used anymore.
   */
  @Deprecated
  default ActionCallback getActivation() {
    return ActionCallback.DONE;
  }

  void setTitleActions(@NotNull List<AnAction> actions);
}
