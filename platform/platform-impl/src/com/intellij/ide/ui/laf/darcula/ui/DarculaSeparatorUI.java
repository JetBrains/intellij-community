// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.JBValue;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicSeparatorUI;
import java.awt.*;

public class DarculaSeparatorUI extends BasicSeparatorUI {
  private static final JBValue STRIPE_WIDTH = new JBValue.Float(1);
  private static final JBValue STRIPE_INDENT = new JBValue.Float(1);

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaSeparatorUI();
  }

  @Override
  protected void installDefaults(JSeparator s) {
    Color bg = s.getForeground();
    if (bg == null || bg instanceof UIResource) {
      s.setForeground(JBColor.namedColor(getColorResourceName(), new JBColor(Gray.xCD, Gray.x51)));
    }

    LookAndFeel.installProperty(s, "opaque", Boolean.FALSE);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    Rectangle r = new Rectangle(c.getSize());
    g.setColor(c.getBackground());
    g.fillRect(0, 0, c.getWidth(), c.getHeight());

    g.setColor(c.getForeground());
    if (((JSeparator)c).getOrientation() == SwingConstants.VERTICAL) {
      g.fillRect(r.x + getStripeIndent(), r.y, getStripeWidth(), r.height);
    }
    else {
      int withToEdge = getWithToEdge();
      g.fillRect(r.x + withToEdge, r.y + getStripeIndent(), r.width - withToEdge * 2, getStripeWidth());
    }
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return ((JSeparator)c).getOrientation() == SwingConstants.VERTICAL ?
           JBUI.size(3, 0) : JBUI.size(0, 3);
  }

  protected int getStripeIndent() {
    return STRIPE_INDENT.get();
  }

  protected int getStripeWidth() {
    return STRIPE_WIDTH.get();
  }

  protected int getWithToEdge() {
    return 0;
  }

  protected String getColorResourceName() {
    return "Separator.separatorColor";
  }
}
