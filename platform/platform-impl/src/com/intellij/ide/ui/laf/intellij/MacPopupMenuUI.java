// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBValue;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicPopupMenuUI;
import java.awt.*;
import java.awt.event.MouseEvent;

public class MacPopupMenuUI extends BasicPopupMenuUI {
  private static final JBValue CORNER_RADIUS = new JBValue.UIInteger("Menu.borderCornerRadius", 8);

  public MacPopupMenuUI() {
  }

  public static ComponentUI createUI(final JComponent c) {
    return new MacPopupMenuUI();
  }

  @Override
  public boolean isPopupTrigger(final MouseEvent event) {
    return event.isPopupTrigger();
  }

  @Override
  public void paint(final Graphics g, final JComponent jcomponent) {
    GraphicsUtil.setupAntialiasing(g, true, true);
    super.paint(g, jcomponent);

    Rectangle rectangle = popupMenu.getBounds();
    int cornerRadius = CORNER_RADIUS.get();
    g.setColor(popupMenu.getBackground());
    g.fillRoundRect(0, 0, rectangle.width, rectangle.height, cornerRadius, cornerRadius);
    g.setColor(JBColor.namedColor("Menu.borderColor", new JBColor(Gray.xCD, Gray.x51)));
    g.drawRoundRect(0, 0, rectangle.width, rectangle.height, cornerRadius, cornerRadius);
  }
}
