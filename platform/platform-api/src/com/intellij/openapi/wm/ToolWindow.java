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
package com.intellij.openapi.wm;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Optional;

public interface ToolWindow extends BusyObject {

  Key<Boolean> SHOW_CONTENT_ICON = new Key<>("ContentIcon");

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  boolean isActive();

  /**
   * @param runnable A command to execute right after the window gets activated.  The call is asynchronous since it may require animation.
   * @exception IllegalStateException if tool window isn't installed.
   */
  void activate(@Nullable Runnable runnable);

  void activate(@Nullable Runnable runnable, boolean autoFocusContents);

  void activate(@Nullable Runnable runnable, boolean autoFocusContents, boolean forced);

  /**
   * Activates the tool window.
   *
   * @param runnable A command to execute right after the window gets activated. The call is asynchronous since it may require animation.
   * @param selectedFile A file that user have selected to open the window.
   */
  default void activate(@Nullable Runnable runnable, @Nullable VirtualFile selectedFile) {
  }

  /**
   * Gets the file that user have opened the window for.
   *
   * @return the file, selected by user or {@link Optional#empty()}, if the user have selected nothing or file info is unavailable.
   */
  default Optional<VirtualFile> getSelectedFile() {
    return Optional.empty();
  }

  /**
   * @return whether the tool window is visible or not.
   * @exception IllegalStateException if tool window isn't installed.
   */
  boolean isVisible();

  /**
   * @param runnable A command to execute right after the window shows up.  The call is asynchronous since it may require animation.
   * @exception IllegalStateException if tool window isn't installed.
   */
  void show(@Nullable Runnable runnable);

  /**
   * Hides tool window. If the window is active then the method deactivates it.
   * Does nothing if tool window isn't visible.
   * @param runnable A command to execute right after the window hides.  The call is asynchronous since it may require animation.
   * @exception IllegalStateException if tool window isn't installed.
   */
  void hide(@Nullable Runnable runnable);

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  ToolWindowAnchor getAnchor();

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  void setAnchor(@NotNull ToolWindowAnchor anchor, @Nullable Runnable runnable);

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  boolean isSplitMode();

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  void setSplitMode(boolean split, @Nullable Runnable runnable);

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  boolean isAutoHide();

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  void setAutoHide(boolean state);

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  ToolWindowType getType();

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  void setType(@NotNull ToolWindowType type, @Nullable Runnable runnable);

  /**
   * @return window icon. Returns {@code null} if window has no icon.
   */
  Icon getIcon();

  /**
   * Sets new window icon.
   */
  void setIcon(Icon icon);

  /**
   * @return window title. Returns {@code null} if window has no title.
   */
  String getTitle();

  /**
   * Sets new window title.
   */
  void setTitle(String title);

  /**
   * @return window stripe button text.
   */
  @NotNull
  String getStripeTitle();

  /**
   * Sets new window stripe button text.
   */
  void setStripeTitle(@NotNull String title);

  /**
   * @return whether the window is available or not.
   */
  boolean isAvailable();

  /**
   * Sets whether the tool window available or not. Term "available" means that tool window
   * can be shown and it has button on tool window bar.
   * @exception IllegalStateException if tool window isn't installed.
   */
  void setAvailable(boolean available, @Nullable Runnable runnable);

  void setContentUiType(@NotNull ToolWindowContentUiType type, @Nullable Runnable runnable);
  void setDefaultContentUiType(@NotNull ToolWindowContentUiType type);

  @NotNull
  ToolWindowContentUiType getContentUiType();

  void installWatcher(ContentManager contentManager);

  /**
   * @return component which represents window content.
   */
  JComponent getComponent();


  ContentManager getContentManager();


  void setDefaultState(@Nullable ToolWindowAnchor anchor, @Nullable ToolWindowType type, @Nullable Rectangle floatingBounds);


  void setToHideOnEmptyContent(boolean hideOnEmpty);

  boolean isToHideOnEmptyContent();

  /**
   *
   * @param show if {@code false} stripe button would be hidden
   */
  void setShowStripeButton(boolean show);

  boolean isShowStripeButton();

  boolean isDisposed();

  void showContentPopup(InputEvent inputEvent);

  ActionCallback getActivation();

  class Border extends EmptyBorder {
    public Border() {
      this(true, true, true, true);
    }

    public Border(boolean top, boolean left, boolean right, boolean bottom) {
      super(top ? 2 : 0, left ? 2 : 0, right ? 2 : 0, bottom ? 2 : 0);
    }
  }

}
