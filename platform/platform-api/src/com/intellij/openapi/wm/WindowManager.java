// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Provides access to IDE's frames and status bar.
 */
public abstract class WindowManager {

  /**
   * @return {@code true} if current OS supports alpha mode for windows and all native libraries were successfully loaded.
   */
  public abstract boolean isAlphaModeSupported();

  /**
   * Sets alpha (transparency) ratio for the specified {@code window}.
   * <p>
   * If alpha mode isn't supported by the underlying windowing system, then the method does nothing.
   * The method also does nothing if alpha mode isn't enabled for the specified {@code window}.
   *
   * @param window {@code window} which transparency should be changed.
   * @param ratio  ratio of transparency. {@code 0} means absolutely non transparent window.
   *               {@code 1} means absolutely transparent window.
   * @throws IllegalArgumentException if {@code window} is not displayable or not showing, or if {@code ration} isn't in {@code [0..1]} range.
   */
  public abstract void setAlphaModeRatio(Window window, float ratio);

  /**
   * @return {@code true} if specified {@code window} is currently is alpha mode.
   */
  public abstract boolean isAlphaModeEnabled(Window window);

  /**
   * Sets whether the alpha (transparent) mode is enabled for specified {@code window}.
   * If alpha mode isn't supported by the underlying windowing system, then the method does nothing.
   *
   * @param window window which mode to be set.
   * @param state  determines the new alpha mode.
   */
  public abstract void setAlphaModeEnabled(Window window, boolean state);

  public static WindowManager getInstance() {
    return ApplicationManager.getApplication().getComponent(WindowManager.class);
  }

  public abstract void doNotSuggestAsParent(Window window);

  /**
   * Gets first window (starting from the active one) that can be the parent for other windows.
   * Note, that this method returns only subclasses of {@link Dialog} or {@link Frame}.
   *
   * @return {@code null} if there is no currently active window or there is no window that can be the parent.
   */
  @Nullable
  public abstract Window suggestParentWindow(@Nullable Project project);

  /**
   * Get the status bar for the project's main frame.
   */
  public abstract StatusBar getStatusBar(@NotNull Project project);

  public StatusBar getStatusBar(@NotNull Component c, @Nullable Project project) {
    return null;
  }

  @Nullable
  public abstract JFrame getFrame(@Nullable Project project);

  @Nullable
  public abstract IdeFrame getIdeFrame(@Nullable Project project);

  /**
   * Tests whether the specified rectangle is inside of screen bounds.
   * <p>
   * Method uses its own heuristic test. Test passes if the intersection of screen bounds and specified rectangle
   * isn't empty and its height and width are not less than some value.
   * Note, that all parameters are in screen coordinate system. The method properly works in a multi-monitor configuration.
   */
  public abstract boolean isInsideScreenBounds(int x, int y, int width);

  @NotNull
  public abstract IdeFrame[] getAllProjectFrames();

  public abstract JFrame findVisibleFrame();

  public abstract void addListener(WindowManagerListener listener);

  public abstract void removeListener(WindowManagerListener listener);

  /**
   * @return {@code true} if full screen mode is supported in current OS.
   */
  @SuppressWarnings("unused")
  public abstract boolean isFullScreenSupportedInCurrentOS();

  public abstract void requestUserAttention(@NotNull IdeFrame frame, boolean critical);
}
