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

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.TextFieldWithPopupHandlerUI;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.Gray;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class MacIntelliJTextBorder extends DarculaTextBorder {
  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(3, 6).asUIResource();
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    if (TextFieldWithPopupHandlerUI.isSearchField(c)) {
      return;
    }

    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.translate(x, y);
      RectanglePainter.paint(g2, JBUI.scale(3), JBUI.scale(3),
                             c.getWidth() - JBUI.scale(6),
                             c.getHeight() - JBUI.scale(6), 0, null, Gray.xBC);

      if (c.getParent() instanceof JComboBox) return;

      paint(c, g2, width, height, 0);
    } finally {
      g2.dispose();
    }
  }

  public void paint(Component c, Graphics2D g2, int width, int height, int arc) {
    clipForBorder(c, g2, width, height);

    Object eop = ((JComponent)c).getClientProperty("JComponent.error.outline");
    if (Registry.is("ide.inplace.errors.outline") && Boolean.parseBoolean(String.valueOf(eop))) {
      DarculaUIUtil.paintErrorBorder(g2, width, height, arc, isFocused(c));
    } else if (isFocused(c)) {
      DarculaUIUtil.paintFocusBorder(g2, width, height, JBUI.scale(4), arc);
    }
  }

  boolean isFocused(Component c) {
    return c.hasFocus();
  }

  void clipForBorder(Component c, Graphics2D g2, int width, int height) {
    Area area = new Area(new Rectangle2D.Double(0, 0, width, height));
    area.subtract(new Area(new Rectangle2D.Double(JBUI.scale(4), JBUI.scale(4),
                                                  width - JBUI.scale(8),
                                                  height - JBUI.scale(8))));
    area.intersect(new Area(g2.getClip()));
    g2.setClip(area);
  }
}
