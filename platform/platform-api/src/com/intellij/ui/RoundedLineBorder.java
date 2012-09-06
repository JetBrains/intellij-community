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
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * @author Eugene Belyaev
 */
public class RoundedLineBorder extends LineBorder {
  private int myArcSize = 1;

  public RoundedLineBorder(Color color) {
    super(color);
  }

  public RoundedLineBorder(Color color, int arcSize) {
    this(color, arcSize, 1);
  }

  public RoundedLineBorder(Color color, int arcSize, final int thickness) {
    super(color, thickness);
    myArcSize = arcSize;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    final Graphics2D g2 = (Graphics2D)g;

    final Object oldAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    final Color oldColor = g2.getColor();
    g2.setColor(lineColor);

    for (int i = 0; i < thickness; i++) {
      g2.drawRoundRect(x + i, y + i, width - i - i - 1, height - i - i - 1, myArcSize, myArcSize);
    }

    g2.setColor(oldColor);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing);
  }

  public void setColor(@NotNull Color color) {
    lineColor = color;
  }
}
