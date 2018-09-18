// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicSeparatorUI;
import java.awt.*;

public class DarculaSeparatorUI extends BasicSeparatorUI {
  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c )
  {
    return new DarculaSeparatorUI();
  }

  @Override
  protected void installDefaults(JSeparator s) {
    Color bg = s.getBackground();
    if (bg == null || bg instanceof UIResource) {
      s.setForeground(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground());
    }

    LookAndFeel.installProperty( s, "opaque", Boolean.FALSE);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Rectangle r = new Rectangle(c.getSize());

    g.setColor(c.getForeground());

    if (((JSeparator)c).getOrientation() == SwingConstants.VERTICAL) {
      g.fillRect(r.x + JBUI.scale(1), r.y, JBUI.scale(1), r.height);
    }
    else {
      g.fillRect(r.x, r.y + JBUI.scale(1), r.width, JBUI.scale(1));
    }
  }

  @Override
  public Dimension getPreferredSize(JComponent c){
    return ((JSeparator)c).getOrientation() == SwingConstants.VERTICAL ?
           JBUI.size(3, 0) : JBUI.size(0, 3);
  }
}
