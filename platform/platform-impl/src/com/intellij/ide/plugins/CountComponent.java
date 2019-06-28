// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class CountComponent extends JLabel {
  @SuppressWarnings("UseJBColor")
  private final Color myOvalColor = JBColor.namedColor("Counter.background", new Color(0xCC9AA7B0, true));

  public CountComponent() {
    setBorder(null);
    setFont(UIUtil.getLabelFont(SystemInfo.isMac || (SystemInfo.isLinux && (UIUtil.isUnderIntelliJLaF() || UIUtil.isUnderDarcula()))
                                ? UIUtil.FontSize.SMALL
                                : UIUtil.FontSize.NORMAL));
    setForeground(JBColor.namedColor("Counter.foreground", new JBColor(0xFFFFFF, 0x3E434D)));
    setHorizontalAlignment(CENTER);
    setHorizontalTextPosition(CENTER);
  }

  public void setSelected(boolean selected) {
    setBackground(selected ? UIUtil.getTreeSelectionBackground(true) : UIUtil.SIDE_PANEL_BACKGROUND);
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension size = super.getPreferredSize();
    return new Dimension(Math.max(size.width, getTextOffset() + getOvalWidth()), Math.max(size.height, getOvalHeight()));
  }

  @Override
  protected void paintComponent(Graphics g) {
    int corner = JBUIScale.scale(14);
    int ovalWidth = getOvalWidth();
    int ovalHeight = getOvalHeight();
    int width = getWidth();
    int height = getHeight();

    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);

    if (isOpaque()) {
      g.setColor(getBackground());
      g.fillRect(0, 0, width, height);
    }

    g.setColor(myOvalColor);
    g.fillRoundRect(getTextOffset() + (width - ovalWidth) / 2, (height - ovalHeight) / 2, ovalWidth, ovalHeight, corner, corner);

    config.restore();

    super.paintComponent(g);
  }

  private int getOvalWidth() {
    int i = getText().length() == 1 ? 16 : 20;
    return JBUIScale.scale(i);
  }

  private int getTextOffset() {
    String text = getText();
    return text.equals("1") || text.equals("3") || text.equals("4") ? 1 : 0;
  }

  private static int getOvalHeight() {
    return JBUIScale.scale(14);
  }
}