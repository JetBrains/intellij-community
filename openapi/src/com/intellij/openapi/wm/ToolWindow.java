/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.wm;

import com.intellij.ui.content.ContentManager;

import javax.swing.*;

public interface ToolWindow {
  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  boolean isActive();

  /**
   * @param runnable A command to execute right after the window gets activated.  The call is asynchronous since it may require animation.
   * @exception IllegalStateException if tool window isn't installed.
   */
  void activate(Runnable runnable);

  /**
   * @return whether the tool window is visible or not.
   * @exception IllegalStateException if tool window isn't installed.
   */
  boolean isVisible();

  /**
   * @param runnable A command to execute right after the window shows up.  The call is asynchronous since it may require animation.
   * @exception IllegalStateException if tool window isn't installed.
   */
  void show(Runnable runnable);

  /**
   * Hides tool window. If the window is active then the method deactivates it.
   * Does nothing if tool window isn't visible.
   * @param runnable A command to execute right after the window hides.  The call is asynchronous since it may require animation.
   * @exception IllegalStateException if tool window isn't installed.
   */
  void hide(Runnable runnable);

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  ToolWindowAnchor getAnchor();

  /**
   * @exception IllegalStateException if tool window isn't installed.
   */
  void setAnchor(ToolWindowAnchor anchor, Runnable runnable);

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
  void setType(ToolWindowType type, Runnable runnable);

  /**
   * @return window icon. Returns <code>null</code> if window has no icon.
   */
  Icon getIcon();

  /**
   * Sets new window icon.
   */
  void setIcon(Icon icon);

  /**
   * @return window title. Returns <code>null</code> if window has no title.
   */
  String getTitle();

  /**
   * Sets new window title.
   * @exception IllegalStateException if tool window isn't installed.
   */
  void setTitle(String title);

  /**
   * @return whether the window is available or not.
   */
  boolean isAvailable();

  /**
   * Sets whether the tool window available or not. Term "available" means that tool window
   * can be shown and it has button on tool window bar.
   * @exception IllegalStateException if tool window isn't installed.
   */
  void setAvailable(boolean available, Runnable runnable);

  void installWatcher(ContentManager contentManager);

  /**
   * @return component which represents window content.
   */
  JComponent getComponent();
}