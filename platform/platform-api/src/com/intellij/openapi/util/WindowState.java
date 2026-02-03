// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.wm.IdeFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Window;

public interface WindowState {
  /**
   * @return a window location
   * @see Window#getLocation()
   */
  @Nullable
  Point getLocation();

  /**
   * @return a window size
   * @see Window#getSize()
   */
  @Nullable
  Dimension getSize();

  /**
   * @return a bitwise mask that represents an extended frame state
   * @see Frame#getExtendedState()
   */
  int getExtendedState();

  /**
   * @return {@code true} if a frame should be opened in a full screen mode
   * @see IdeFrame#isInFullScreen()
   */
  boolean isFullScreen();

  /**
   * @param window a window to apply this state
   */
  void applyTo(@NotNull Window window);
}
