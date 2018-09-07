// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class TagComponent extends LinkComponent {
  private final Color myColor;

  public TagComponent(@NotNull String name, @Nullable String tooltip, @NotNull Color color) {
    myColor = color;
    setText(name);
    if (tooltip != null) {
      setToolTipText(tooltip);
    }
    setForeground(new JBColor(0x787878, 0x999999));
    setPaintUnderline(false);
    setOpaque(false);
    setBorder(JBUI.Borders.empty(1, 8));
  }

  @Override
  protected void paintComponent(Graphics g) {
    //noinspection UseJBColor
    g.setColor(myUnderline ? new Color(myColor.getRed(), myColor.getGreen(), myColor.getBlue(), 178) : myColor);
    g.fillRect(0, 0, getWidth(), getHeight());
    super.paintComponent(g);
  }

  @Override
  protected boolean isInClickableArea(Point pt) {
    return true;
  }
}