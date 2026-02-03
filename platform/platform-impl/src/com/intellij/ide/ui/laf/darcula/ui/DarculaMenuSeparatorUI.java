// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.intellij.IdeaPopupMenuUI;
import com.intellij.util.ui.JBValue;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

public class DarculaMenuSeparatorUI extends DarculaSeparatorUI {
  private static final JBValue SEPARATOR_HEIGHT = new JBValue.UIInteger("PopupMenuSeparator.height", 3);
  private static final JBValue STRIPE_WIDTH = new JBValue.UIInteger("PopupMenuSeparator.stripeWidth", 1);
  private static final JBValue STRIPE_INDENT = new JBValue.UIInteger("PopupMenuSeparator.stripeIndent", 1);
  private static final JBValue WITH_TO_EDGE = new JBValue.UIInteger("PopupMenuSeparator.withToEdge", 1);

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c) {
    return new DarculaMenuSeparatorUI();
  }

  private boolean myIsUnderPopup;

  @Override
  public void paint(Graphics g, JComponent c) {
    myIsUnderPopup = IdeaPopupMenuUI.isPartOfPopupMenu(c);
    super.paint(g, c);
  }

  @Override
  protected String getColorResourceName() {
    return "Menu.separatorColor";
  }

  @Override
  protected int getStripeIndent() {
    return myIsUnderPopup ? STRIPE_INDENT.get() : super.getStripeIndent();
  }

  @Override
  protected int getStripeWidth() {
    return myIsUnderPopup ? STRIPE_WIDTH.get() : super.getStripeWidth();
  }

  @Override
  protected int getWithToEdge() {
    return myIsUnderPopup ? WITH_TO_EDGE.get() : super.getWithToEdge();
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    if (!IdeaPopupMenuUI.isPartOfPopupMenu(c)) {
      return super.getPreferredSize(c);
    }

    return ((JSeparator)c).getOrientation() == SwingConstants.VERTICAL
           ?
           new Dimension(SEPARATOR_HEIGHT.get(), 0)
           : new Dimension(0, SEPARATOR_HEIGHT.get()); // height is prescaled, so use Dimension here.
  }
}
