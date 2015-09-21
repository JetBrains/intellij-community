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

import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJProgressBarUI extends DarculaProgressBarUI {

  public static final int HEIGHT = 6;

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new MacIntelliJProgressBarUI();
  }


  @Override
  protected void paintDeterminate(Graphics g, JComponent c) {
    Insets insets = progressBar.getInsets();
    int w = c.getWidth();
    int h = c.getHeight();
    int y = (h - HEIGHT) / 2;
    int x = insets.left;

    Icon icon;
    icon = MacIntelliJIconCache.getIcon("progressLeft");
    icon.paintIcon(c, g, x, y);
    x += icon.getIconWidth();
    int stop = w - (MacIntelliJIconCache.getIcon("progressRight").getIconWidth());
    Graphics gg = g.create(0, 0, w, h);
    gg.setClip(x, y, stop - x, h);
    icon = MacIntelliJIconCache.getIcon("progressMiddle");
    while (x < stop) {
      icon.paintIcon(c, gg, x, y);
      x += icon.getIconWidth();
    }
    gg.dispose();
    icon = MacIntelliJIconCache.getIcon("progressRight");
    icon.paintIcon(c, g, stop, y);

    int barRectWidth = w - (insets.right + insets.left);
    int barRectHeight = h - (insets.top + insets.bottom);

    int amountFull = getAmountFull(insets, barRectWidth, barRectHeight);
    boolean done = amountFull == barRectWidth;
    Icon left = MacIntelliJIconCache.getIcon("progressLeft", true, false);
    Icon middle = MacIntelliJIconCache.getIcon("progressMiddle", true, false);
    Icon right = MacIntelliJIconCache.getIcon("progressRight", true, false);

    gg = g.create(0, 0, barRectWidth + insets.left - right.getIconWidth(), h);
    gg.setClip(insets.left, y, amountFull - right.getIconWidth(), HEIGHT);
    int cur = left.getIconWidth() + insets.left;
    if (cur <= amountFull) {
      left.paintIcon(c, gg, insets.left, y);
    }
    while (cur < amountFull) {
      middle.paintIcon(c, gg, cur, y);
      cur+=middle.getIconWidth();
    }
    gg.dispose();
    if (done) {
      right.paintIcon(c, g, insets.left + barRectWidth - right.getIconWidth(), y);
    }
  }

  protected volatile int position = 0;
  @Override
  protected void paintIndeterminate(Graphics g, JComponent c) {
    Insets insets = progressBar.getInsets();
    int w = c.getWidth();
    int h = c.getHeight();
    int y = (h - HEIGHT) / 2 + insets.top;
    int x = insets.left;

    Icon icon;
    icon = MacIntelliJIconCache.getIcon("progressLeft", true, false);
    icon.paintIcon(c, g, x, y);
    x += icon.getIconWidth();
    int stop = w  - MacIntelliJIconCache.getIcon("progressRight", true, false).getIconWidth() - insets.right;
    Graphics gg = g.create(0, 0, w, h);
    gg.setClip(x, y, stop - x, h);
    icon = MacIntelliJIconCache.getIcon("progressMiddle", true, false);
    while (x < stop) {
      icon.paintIcon(c, gg, x, y);
      x += icon.getIconWidth();
    }
    gg.dispose();
    icon = MacIntelliJIconCache.getIcon("progressRight", true, false);
    icon.paintIcon(c, g, stop, y);

    Icon shadow = MacIntelliJIconCache.getIcon("progressShadow");
    int boxWidth = w - insets.left - insets.right;
    if (boxWidth <= 0) return;
    gg = g.create(0, 0, w, h);
    gg.setClip(new RoundRectangle2D.Double(insets.left, y, boxWidth, HEIGHT, HEIGHT, HEIGHT));
    shadow.paintIcon(c, gg, position, y);
    int xx = boxWidth - (position + shadow.getIconWidth());
    if (xx < 0) {
      shadow.paintIcon(c, gg, -xx - shadow.getIconWidth(), y);
    }
    position++;
    position = position % boxWidth;
    gg.dispose();
  }
}
