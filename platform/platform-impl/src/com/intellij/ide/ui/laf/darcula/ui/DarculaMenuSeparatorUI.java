// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.util.ui.JBValue;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

public class DarculaMenuSeparatorUI extends DarculaSeparatorUI {
  private static final JBValue SEPARATOR_HEIGHT = new JBValue.UIInteger("PopupMenuSeparator.height", 3);
  private static final JBValue STRIPE_WIDTH = new JBValue.UIInteger("PopupMenuSeparator.stripeWidth", 1);
  private static final JBValue STRIPE_INDENT = new JBValue.UIInteger("PopupMenuSeparator.stripeIndent", 1);

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaMenuSeparatorUI();
  }

  @Override
  protected String getColorResourceName() {
    return "Menu.separatorColor";
  }

  @Override
  protected int getStripeIndent() {
    return STRIPE_INDENT.get();
  }

  @Override
  protected int getStripeWidth() {
    return STRIPE_WIDTH.get();
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return ((JSeparator)c).getOrientation() == SwingConstants.VERTICAL
           ?
           new Dimension(SEPARATOR_HEIGHT.get(), 0)
           : new Dimension(0, SEPARATOR_HEIGHT.get()); // height is prescaled, so use Dimension here.
  }
}
