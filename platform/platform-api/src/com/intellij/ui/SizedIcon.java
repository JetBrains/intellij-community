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

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;

/**
 * @author peter
 */
public class SizedIcon extends JBUI.CachingScalableJBIcon implements IconLoader.MenuBarIconProvider {
  private final int myWidth;
  private final int myHeight;
  private final Icon myDelegate;
  private Icon myScaledDelegate;

  public SizedIcon(Icon delegate, int width, int height) {
    myScaledDelegate = myDelegate = delegate;
    myWidth = width;
    myHeight = height;
  }

  protected SizedIcon(SizedIcon icon) {
    super(icon);
    myWidth = icon.myWidth;
    myHeight = icon.myHeight;
    myDelegate = icon.myDelegate;
    myScaledDelegate = null;
  }

  @NotNull
  @Override
  protected SizedIcon copy() {
    return new SizedIcon(this);
  }

  private Icon myScaledIcon() {
    if (myScaledDelegate != null) {
      return myScaledDelegate;
    }
    if (getScale() == 1f) {
      return myScaledDelegate = myDelegate;
    }
    if (!(myDelegate instanceof ScalableIcon)) {
      return myScaledDelegate = myDelegate;
    }
    return myScaledDelegate = ((ScalableIcon)myDelegate).scale(getScale());
  }

  @Override
  public Icon getMenuBarIcon(boolean isDark) {
    return new SizedIcon(IconLoader.getMenuBarIcon(myDelegate, isDark), myWidth, myHeight);
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Icon icon = myScaledIcon();
    double dx = scaleVal(myWidth) - icon.getIconWidth();
    double dy = scaleVal(myHeight) - icon.getIconHeight();
    if (dx > 0 || dy > 0) {
      icon.paintIcon(c, g, x + (int)floor(dx / 2), y + (int)floor(dy / 2));
    }
    else {
      icon.paintIcon(c, g, x, y);
    }
  }

  public int getIconWidth() {
    return (int)ceil(scaleVal(myWidth));
  }

  public int getIconHeight() {
    return (int)ceil(scaleVal(myHeight));
  }
}
