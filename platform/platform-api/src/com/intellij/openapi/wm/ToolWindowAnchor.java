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

import com.intellij.ide.ui.UISettings;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public final class ToolWindowAnchor {
  public static final ToolWindowAnchor TOP = new ToolWindowAnchor("top");
  public static final ToolWindowAnchor LEFT = new ToolWindowAnchor("left");
  public static final ToolWindowAnchor BOTTOM = new ToolWindowAnchor("bottom");
  public static final ToolWindowAnchor RIGHT = new ToolWindowAnchor("right");

  private final String myText;

  private ToolWindowAnchor(@NonNls String text){
    myText = text;
  }

  public String toString(){
    return myText;
  }

  public boolean isHorizontal() {
    return this == TOP || this == BOTTOM;
  }

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
    return (this == LEFT && !UISettings.getInstance().LEFT_HORIZONTAL_SPLIT) || (this == RIGHT && !UISettings.getInstance().RIGHT_HORIZONTAL_SPLIT);
  }

  public static ToolWindowAnchor fromText(String anchor) {
    for (ToolWindowAnchor a : new ToolWindowAnchor[]{TOP, LEFT, BOTTOM, RIGHT}) {
      if (a.myText.equals(anchor)) return a;
    }
    throw new IllegalArgumentException("Unknown anchor constant: " + anchor);
  }
}
