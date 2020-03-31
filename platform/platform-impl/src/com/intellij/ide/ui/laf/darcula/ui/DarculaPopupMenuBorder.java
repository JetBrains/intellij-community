// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    }
    finally {
      g2.dispose();
    }
  }

  private static Shape getBorderShape(Component c, Rectangle rect) {
    Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
    if (isComboPopup(c) && ((BasicComboPopup)c).getClientProperty("JComboBox.isCellEditor") == Boolean.TRUE) {
      JBInsets.removeFrom(rect, JBInsets.create(0, 1));
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
      return JBInsets.create(1, 2).asUIResource();
    }
    else {
      return JBUI.insets("PopupMenu.borderInsets", DEFAULT_INSETS).asUIResource();
    }
  }

  protected static boolean isComboPopup(Component c) {
    return "ComboPopup.popup".equals(c.getName()) && c instanceof BasicComboPopup;
  }
}
