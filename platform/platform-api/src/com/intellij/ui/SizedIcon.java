/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class SizedIcon extends JBUI.ScalableJBIcon {
  private final int myWidth;
  private final int myHeight;
  private final Icon myDelegate;
  private Icon myScaledDelegate;

  public SizedIcon(Icon delegate, int width, int height) {
    myScaledDelegate = myDelegate = delegate;
    myWidth = width;
    myHeight = height;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    int dx = scaleVal(myWidth) - myScaledDelegate.getIconWidth();
    int dy = scaleVal(myHeight) - myScaledDelegate.getIconHeight();
    if (dx > 0 || dy > 0) {
      myScaledDelegate.paintIcon(c, g, x + dx/2, y + dy/2);
    }
    else {
      myScaledDelegate.paintIcon(c, g, x, y);
    }
  }

  public int getIconWidth() {
    return scaleVal(myWidth);
  }

  public int getIconHeight() {
    return scaleVal(myHeight);
  }

  @Override
  public Icon scale(float scale) {
    if (scale == 1f) {
      myScaledDelegate = myDelegate;
    } else if (myDelegate instanceof ScalableIcon) {
      myScaledDelegate = ((ScalableIcon)myDelegate).scale(scale);
    }
    return super.scale(scale);
  }
}
