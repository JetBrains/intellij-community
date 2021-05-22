// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.JBInsets;

import java.awt.*;

import static com.intellij.ui.paint.RectanglePainter.FILL;
import static javax.swing.SwingConstants.*;
import static javax.swing.SwingUtilities.layoutCompoundLabel;

public class GroupHeaderSeparator extends SeparatorWithText {

  private boolean myHideLine;
  private final Insets myLabelInsets;

  public GroupHeaderSeparator(Insets insets) {myLabelInsets = insets;}

  public void setHideLine(boolean hideLine) {
    myHideLine = hideLine;
  }

  @Override
  protected Dimension getPreferredElementSize() {
    Dimension size = new Dimension(Math.max(myPrefWidth, 0), 0);
    if (!StringUtil.isEmpty(getCaption())) {
      size = getLabelSize();
      size.height += myLabelInsets.top + myLabelInsets.bottom;
    }
    if (!myHideLine) size.height += getVgap() + 1;

    JBInsets.addTo(size, getInsets());
    return size;
  }

  @Override
  protected void paintComponent(Graphics g) {
    g.setColor(getForeground());

    Rectangle bounds = new Rectangle(getWidth(), getHeight());
    JBInsets.removeFrom(bounds, getInsets());

    if (!myHideLine) {
      paintLine(g, bounds);
      bounds.y += getVgap() + 1;
    }

    String caption = getCaption();
    if (caption != null) {
      bounds.x += myLabelInsets.left;
      bounds.width -= myLabelInsets.left + myLabelInsets.right;
      bounds.y += myLabelInsets.top;
      bounds.height -= myLabelInsets.top + myLabelInsets.bottom;

      Rectangle iconR = new Rectangle();
      Rectangle textR = new Rectangle();
      FontMetrics fm = g.getFontMetrics();
      String label = layoutCompoundLabel(fm, caption, null, CENTER, LEFT, CENTER, LEFT, bounds, iconR, textR, 0);
      UISettings.setupAntialiasing(g);
      g.setColor(getTextForeground());
      g.drawString(label, textR.x, textR.y + fm.getAscent());
    }
  }

  private static void paintLine(Graphics g, Rectangle bounds) {
    int x = bounds.x + getHgap();
    int width = bounds.width - getHgap() * 2;
    int y = bounds.y + getVgap();
    FILL.paint((Graphics2D)g, x, y, width, 1, null);
  }
}
