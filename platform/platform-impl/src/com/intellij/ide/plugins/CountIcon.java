// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins;

import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public final class CountIcon extends CountComponent implements Icon {
  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    if (isEmpty()) {
      return;
    }

    Dimension size = getPreferredSize();
    setSize(size);
    Graphics2D g2 = (Graphics2D)g.create(x, y, size.width, size.height);
    paint(g2);
    g2.dispose();
  }

  private boolean isEmpty() {
    String text = getText();
    return StringUtil.isEmpty(text) || "0".equals(text);
  }

  @Override
  public int getIconWidth() {
    return isEmpty() ? 0 : getPreferredSize().width;
  }

  @Override
  public int getIconHeight() {
    return isEmpty() ? 0 : getPreferredSize().height;
  }
}