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

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class SizedIcon implements Icon {
  private final int myWidth;
  private final int myHeight;
  private final Icon myDelegate;

  public SizedIcon(Icon delegate, int width, int height) {
    myDelegate = delegate;
    myWidth = width;
    myHeight = height;
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    int dx = myWidth - myDelegate.getIconWidth();
    int dy = myHeight - myDelegate.getIconHeight();
    if (dx > 0 || dy > 0) {
      myDelegate.paintIcon(c, g, x + dx/2, y + dy/2);
    }
    else {
      myDelegate.paintIcon(c, g, x, y);
    }
  }

  public int getIconWidth() {
    return myWidth;
  }

  public int getIconHeight() {
    return myHeight;
  }
}
