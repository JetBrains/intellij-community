/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;
import java.awt.geom.Path2D;

public class WinIntelliJPopupBorder extends AbstractBorder implements UIResource {
  @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setColor(UIManager.getDefaults().getColor("TextField.hoverBorderColor"));
      g2.fill(getBorderShape(c, new Rectangle(x, y, width, height)));
    } finally {
      g2.dispose();
    }
  }

  private static Shape getBorderShape(Component c, Rectangle rect) {
    Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    if ("ComboPopup.popup".equals(c.getName())) {
      JBInsets.removeFrom(rect, JBUI.insets(0, 1));
    }

    border.append(rect, false);

    Rectangle innerRect = new Rectangle(rect);
    JBInsets.removeFrom(innerRect, JBUI.insets(1));
    border.append(innerRect, false);

    return border;
  }

  @Override
  public Insets getBorderInsets(Component c) {
    if ("ComboPopup.popup".equals(c.getName())) {
      return JBUI.insets(1, 2).asUIResource();
    } else {
      return JBUI.insets(1).asUIResource();
    }
  }
}
