// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.border;

import com.intellij.ui.TitledSeparator;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * @author evgeny zakrevsky
 */
public class IdeaTitledBorder extends TitledBorder {

  private final TitledSeparator titledSeparator;
  private final Insets insideInsets;
  private final Insets outsideInsets;
  private boolean myShowLine = true;

  public IdeaTitledBorder(@Nls(capitalization = Nls.Capitalization.Title) String title, int indent, Insets insets) {
    super(title);
    titledSeparator = new TitledSeparator(title);
    titledSeparator.setText(title);
    DialogUtil.registerMnemonic(titledSeparator.getLabel(), null);

    outsideInsets = JBInsets.create(insets);
    insideInsets = new JBInsets(TitledSeparator.BOTTOM_INSET, indent, 0, 0);
  }

  @Override
  public void setTitle(@Nls(capitalization = Nls.Capitalization.Title) String title) {
    super.setTitle(title);
    titledSeparator.setText(title);
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    int labelX = x + outsideInsets.left;
    int labelY = y + outsideInsets.top;

    TitledSeparator titledSeparator = getTitledSeparator(c);
    JLabel label = titledSeparator.getLabel();
    Dimension labelSize = label.getPreferredSize();
    label.setSize(labelSize);
    g.translate(labelX, labelY);
    label.paint(g);

    int separatorX = labelX + labelSize.width + TitledSeparator.SEPARATOR_LEFT_INSET;
    int separatorY = labelY +  labelSize.height / 2 - 1;
    int separatorW = Math.max(0, width - separatorX - TitledSeparator.SEPARATOR_RIGHT_INSET);
    int separatorH = 2;

    JSeparator separator = titledSeparator.getSeparator();
    separator.setSize(separatorW, separatorH);
    g.translate(separatorX - labelX, separatorY - labelY);
    if (myShowLine) {
      separator.paint(g);
    }
    g.translate(-separatorX, -separatorY);
  }

  private TitledSeparator getTitledSeparator(Component c) {
    titledSeparator.setEnabled(c.isEnabled());
    return titledSeparator;
  }

  public IdeaTitledBorder setShowLine(boolean showLine) {
    myShowLine = showLine;
    insideInsets.top = JBUI.scale(myShowLine ? TitledSeparator.BOTTOM_INSET : 3);
    return this;
  }

  public void acceptMinimumSize(Component c) {
    Dimension minimumSize = getMinimumSize(c);
    c.setMinimumSize(new Dimension(Math.max(minimumSize.width, c.getMinimumSize().width),
                                   Math.max(minimumSize.height, c.getMinimumSize().height)));
  }

  public Insets getInsideInsets() {
    return insideInsets;
  }

  public Insets getOutsideInsets() {
    return outsideInsets;
  }

  @Override
  public Dimension getMinimumSize(Component c) {
    Insets insets = getBorderInsets(c);
    Dimension minSize = new Dimension(insets.right + insets.left, insets.top + insets.bottom);
    Dimension separatorSize = getTitledSeparator(c).getPreferredSize();
    minSize.width = Math.max(minSize.width, separatorSize.width + outsideInsets.left + outsideInsets.right);
    return minSize;
  }

  @Override
  public Insets getBorderInsets(Component c, final Insets insets) {
    insets.top += getTitledSeparator(c).getPreferredSize().getHeight() - TitledSeparator.TOP_INSET - TitledSeparator.BOTTOM_INSET;
    insets.top += myShowLine ? UIUtil.DEFAULT_VGAP : 0;
    insets.top += insideInsets.top;
    insets.left += insideInsets.left;
    insets.bottom += insideInsets.bottom;
    insets.right += insideInsets.right;
    insets.top += outsideInsets.top;
    insets.left += outsideInsets.left;
    insets.bottom += outsideInsets.bottom;
    insets.right += outsideInsets.right;
    return insets;
  }
}
