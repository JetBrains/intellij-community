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
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;

/**
 * @author Konstantin Bulenkov
 */
public class MacComboBoxBorder extends MacIntelliJTextBorder {

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();

    try {
      Area area = new Area(new Rectangle2D.Double(x, y, width, height));
      area.subtract(getButtonBounds(c));
      g2.setClip(area);

      g2.setColor(Gray.xBC);
      g2.setStroke(new BasicStroke(1));

      Shape rect = new Rectangle2D.Double(JBUI.scale(3), JBUI.scale(3),
                                          c.getWidth() - JBUI.scale(7),
                                          c.getHeight() - JBUI.scale(7));
      g2.draw(rect);

      paint(c, g2, x, y, width, height);
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
}
