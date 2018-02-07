// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class WinMenuBarBorder implements Border, UIResource {
  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    g.setColor(Gray.xCD);
    UIUtil.drawLine(g, x, y + height - 1, x + width, y + height - 1);
  }

  @Override
  public Insets getBorderInsets(Component c) {
    //noinspection UseDPIAwareInsets
    return new Insets(0, 0, 1, 0);
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }
}
