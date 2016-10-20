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
public class SizedIcon extends JBUI.JBAbstractIcon implements Icon, ScalableIcon {
  private final int myWidth;
  private final int myHeight;
  private final Icon myDelegate;
  private Icon myScaledDelegate;
  private float myScale = 1f;

  public SizedIcon(Icon delegate, int width, int height) {
    myDelegate = delegate;
    myWidth = width;
    myHeight = height;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    int dx = scaleVal(myWidth) - delegate().getIconWidth();
    int dy = scaleVal(myHeight) - delegate().getIconHeight();
    if (dx > 0 || dy > 0) {
      delegate().paintIcon(c, g, x + dx/2, y + dy/2);
    }
    else {
      delegate().paintIcon(c, g, x, y);
    }
  }

  public Icon delegate() {
    return myScaledDelegate != null ? myScaledDelegate : myDelegate;
  }

  @Override
  public int scaleVal(int n) {
    return super.scaleVal(myScale == 1f ? n : (int) (n * myScale));
  }

  public int getIconWidth() {
    if (delegate() instanceof ScalableIcon) {
      return delegate().getIconWidth();
    }
    return scaleVal(myWidth);
  }

  public int getIconHeight() {
    if (delegate() instanceof ScalableIcon) {
      return delegate().getIconHeight();
    }
    return scaleVal(myHeight);
  }

  @Override
  public Icon scale(float scaleFactor) {
    if (scaleFactor == 1f) {
      myScaledDelegate = null;
    } else if (myDelegate instanceof ScalableIcon) {
      myScaledDelegate = ((ScalableIcon)myDelegate).scale(scaleFactor);
    }
    myScale = scaleFactor;
    return this;
  }
}
