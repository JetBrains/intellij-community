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
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import javax.swing.border.AbstractBorder;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicComboPopup;
import java.awt.*;
import java.awt.geom.Path2D;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaPopupMenuBorder extends AbstractBorder implements UIResource {
  private static final JBInsets DEFAULT_INSETS = JBUI.insets(1);

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setColor(JBColor.namedColor("Menu.borderColor", new JBColor(Gray.xCD, Gray.x51)));
      g2.fill(getBorderShape(c, new Rectangle(x, y, width, height)));
    } finally {
      g2.dispose();
    }
  }

  private static Shape getBorderShape(Component c, Rectangle rect) {
    Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    if (isComboPopup(c) && ((BasicComboPopup)c).getClientProperty("JComboBox.isCellEditor") == Boolean.TRUE) {
      JBInsets.removeFrom(rect, JBUI.insets(0, 1));
    }

    border.append(rect, false);

    Rectangle innerRect = new Rectangle(rect);
    JBInsets.removeFrom(innerRect, JBUI.insets(JBUI.getInt("PopupMenu.borderWidth", 1)));
    border.append(innerRect, false);

    return border;
  }

  @Override
  public Insets getBorderInsets(Component c) {
    if (isComboPopup(c)) {
      return JBUI.insets(1, 2).asUIResource();
    } else {
      return JBUI.insets("PopupMenu.borderInsets", DEFAULT_INSETS).asUIResource();
    }
  }

  protected static boolean isComboPopup(Component c) {
    return "ComboPopup.popup".equals(c.getName()) && c instanceof BasicComboPopup;
  }
}
