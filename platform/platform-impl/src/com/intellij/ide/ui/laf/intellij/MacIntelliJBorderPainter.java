/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UnnecessaryLocalVariable")
public class MacIntelliJBorderPainter {
  private static final Icon L = DarculaLaf.loadIcon("macBorderLeft.png");
  private static final Icon R = DarculaLaf.loadIcon("macBorderRight.png");
  private static final Icon T = DarculaLaf.loadIcon("macBorderTop.png");
  private static final Icon B = DarculaLaf.loadIcon("macBorderBottom.png");
  private static final Icon TL = DarculaLaf.loadIcon("macBorderTopLeft.png");
  private static final Icon TR = DarculaLaf.loadIcon("macBorderTopRight.png");
  private static final Icon BL = DarculaLaf.loadIcon("macBorderBottomLeft.png");
  private static final Icon BR = DarculaLaf.loadIcon("macBorderBottomRight.png");

  public static void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    g = g.create(x,y,width,height);
    //corners
    TL.paintIcon(c, g, x, y);
    BL.paintIcon(c, g, x, y + height - BL.getIconHeight());
    TR.paintIcon(c, g, x + width - TR.getIconWidth(), y);
    BR.paintIcon(c, g, x + width - BR.getIconWidth(), y + height - BR.getIconHeight());

    //top and bottom lines
    int xOffset = x + TL.getIconWidth();
    int stop = x + width - TR.getIconWidth();
    int top = y;
    int bottom = y + height - B.getIconHeight();
    g.setClip(xOffset, y, width - L.getIconWidth() - R.getIconWidth(), height);
    while (xOffset < stop) {
      T.paintIcon(c, g, xOffset, top);
      B.paintIcon(c, g, xOffset, bottom);
      xOffset += T.getIconWidth();
    }

    //left and right lines
    int left = x;
    int right = x + width - R.getIconWidth();
    int yOffset = y + T.getIconHeight();
    stop = y + height - B.getIconHeight();
    g.setClip(x, yOffset, width, height - T.getIconHeight() - B.getIconHeight());
    while (yOffset < stop) {
      L.paintIcon(c, g, left, yOffset);
      R.paintIcon(c, g, right, yOffset);
      yOffset += L.getIconHeight();
    }
    g.dispose();
  }
}
