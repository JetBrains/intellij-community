// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.ide.ui.UISettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Anchor for positioning {@link ToolWindow tool window} (TOP, LEFT, BOTTOM, RIGHT).
 */
public final class ToolWindowAnchor {
  public static final @NotNull ToolWindowAnchor TOP = new ToolWindowAnchor("top");
  public static final @NotNull ToolWindowAnchor LEFT = new ToolWindowAnchor("left");
  public static final @NotNull ToolWindowAnchor BOTTOM = new ToolWindowAnchor("bottom");
  public static final @NotNull ToolWindowAnchor RIGHT = new ToolWindowAnchor("right");

  private final @NotNull String myText;

  private ToolWindowAnchor(@NonNls @NotNull String text){
    myText = text;
  }

  public @NotNull String toString() {
    return myText;
  }

  public boolean isHorizontal() {
    return this == TOP || this == BOTTOM;
  }

  public static @NotNull ToolWindowAnchor get(int swingOrientationConstant) {
    switch(swingOrientationConstant) {
      case SwingConstants.TOP:
        return TOP;
      case SwingConstants.BOTTOM:
        return BOTTOM;
      case SwingConstants.LEFT:
        return LEFT;
      case SwingConstants.RIGHT:
        return RIGHT;
    }

    throw new IllegalArgumentException("Unknown anchor constant: " + swingOrientationConstant);
  }

  public boolean isSplitVertically() {
    return this == LEFT && !UISettings.getInstance().getLeftHorizontalSplit()
           || this == RIGHT && !UISettings.getInstance().getRightHorizontalSplit();
  }

  public static @NotNull ToolWindowAnchor fromText(@NotNull String anchor) {
    switch (anchor) {
      case "top":
        return TOP;
      case "left":
        return LEFT;
      case "bottom":
        return BOTTOM;
      case "right":
        return RIGHT;
      default:
        throw new IllegalArgumentException("Unknown anchor constant: " + anchor);
    }
  }
}
