/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.wm;

import com.intellij.ide.ui.UISettings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class ToolWindowAnchor {
  public static final ToolWindowAnchor TOP = new ToolWindowAnchor("top");
  public static final ToolWindowAnchor LEFT = new ToolWindowAnchor("left");
  public static final ToolWindowAnchor BOTTOM = new ToolWindowAnchor("bottom");
  public static final ToolWindowAnchor RIGHT = new ToolWindowAnchor("right");

  @NotNull
  private final String myText;

  private ToolWindowAnchor(@NonNls @NotNull String text){
    myText = text;
  }

  public String toString() {
    return myText;
  }

  public boolean isHorizontal() {
    return this == TOP || this == BOTTOM;
  }

  @NotNull
  public static ToolWindowAnchor get(int swingOrientationConstant) {
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

  @NotNull
  public static ToolWindowAnchor fromText(@Nullable String anchor) {
    for (ToolWindowAnchor a : new ToolWindowAnchor[]{TOP, LEFT, BOTTOM, RIGHT}) {
      if (a.myText.equals(anchor)) {
        return a;
      }
    }
    throw new IllegalArgumentException("Unknown anchor constant: " + anchor);
  }
}
