/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl;

import javax.swing.*;
import java.awt.*;

/**
 * User: cdr
 */
public class TruncatingIcon implements Icon {
  private final int myWidth;
  private final int myHeight;
  private final Icon myDelegate;

  public TruncatingIcon(Icon delegate, int width, int height) {
    myDelegate = delegate;
    myWidth = width;
    myHeight = height;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Shape old = g.getClip();
    g.clipRect(x, y+myDelegate.getIconHeight()-2-myHeight, myWidth, myHeight+2);
    myDelegate.paintIcon(c, g, x, y);
    g.setClip(old);
  }

  @Override
  public int getIconWidth() {
    return myDelegate.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myDelegate.getIconHeight();
  }
}

