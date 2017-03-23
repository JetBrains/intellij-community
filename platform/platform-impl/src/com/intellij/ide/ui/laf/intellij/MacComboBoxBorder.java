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

import com.intellij.ui.Gray;
import com.intellij.ui.paint.RectanglePainter;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class MacComboBoxBorder extends MacIntelliJTextBorder {

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();

    try {
      g2.translate(x, y);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      Area area = new Area(new Rectangle2D.Double(0, 0, width, height));
      area.subtract(getButtonBounds(c));
      g2.setClip(area);

      int arc = isRound(c) ? JBUI.scale(8) : 0;

      if (c instanceof JComboBox) {
        JComboBox comboBox = (JComboBox)c;
        g2.setColor(UIManager.getColor(comboBox.isEnabled() ? "ComboBox.background" : "ComboBox.disabledBackground"));
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        path.moveTo(JBUI.scale(8), JBUI.scale(3));
        path.lineTo(JBUI.scale(8), c.getHeight() - JBUI.scale(3));
        path.lineTo(JBUI.scale(3) + arc, c.getHeight() - JBUI.scale(3));
        path.quadTo(JBUI.scale(3), c.getHeight() - JBUI.scale(3), JBUI.scale(3), c.getHeight() - JBUI.scale(3) - arc);
        path.lineTo(JBUI.scale(3), JBUI.scale(3) + arc);
        path.quadTo(JBUI.scale(3), JBUI.scale(3), JBUI.scale(3) + arc, JBUI.scale(3));
        path.lineTo(JBUI.scale(8), JBUI.scale(3));
        g2.fill(path);
      }

      RectanglePainter.paint(g2, JBUI.scale(3), JBUI.scale(3),
                             c.getWidth() - JBUI.scale(6),
                             c.getHeight() - JBUI.scale(6),
                             arc, null, Gray.xBC);

      paint(c, g2, width, height);
    } finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return new JBInsets(3, 3, 3, 3);
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  boolean isFocused(Component c) {
    if (c.hasFocus()) return true;

    if (c instanceof JComboBox) {
      JComboBox comboBox = (JComboBox)c;
      return comboBox.getEditor() != null && comboBox.getEditor().getEditorComponent().hasFocus();
    }
    return false;
  }

  Area getButtonBounds(Component c) {
    Rectangle bounds = null;
    if (c instanceof JComboBox && ((JComboBox)c).getUI() instanceof MacIntelliJComboBoxUI) {
      MacIntelliJComboBoxUI ui = (MacIntelliJComboBoxUI)((JComboBox)c).getUI();
      bounds = ui.getArrowButtonBounds();
    }
    return bounds != null ? new Area(bounds) : new Area();
  }

  boolean isRound(Component c) {
    return c instanceof JComboBox && !((JComboBox)c).isEditable();
  }

  @Override void clipForBorder(Component c, Graphics2D g2, int width, int height) {
    Area area = new Area(new Rectangle2D.Double(0, 0, width, height));
    Shape innerShape = isRound(c) ?
           new RoundRectangle2D.Double(JBUI.scale(4), JBUI.scale(4),
                                       width - JBUI.scale(8),
                                       height - JBUI.scale(8),
                                       JBUI.scale(10), JBUI.scale(10)) :
           new Rectangle2D.Double(JBUI.scale(4), JBUI.scale(4),
                                  width - JBUI.scale(8),
                                  height - JBUI.scale(8));

    area.subtract(new Area(innerShape));
    area.add(getButtonBounds(c));
    g2.setClip(area);
  }
}
