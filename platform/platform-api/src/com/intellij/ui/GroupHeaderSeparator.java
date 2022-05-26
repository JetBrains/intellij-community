// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;

import java.awt.*;

import static com.intellij.ui.paint.RectanglePainter.FILL;
import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.LEFT;
import static javax.swing.SwingUtilities.layoutCompoundLabel;

public class GroupHeaderSeparator extends SeparatorWithText {

  private boolean myHideLine;
  private final Insets myLabelInsets;
  private final JBInsets lineInsets;

  public GroupHeaderSeparator(Insets labelInsets) {
    myLabelInsets = labelInsets;
    if (ExperimentalUI.isNewUI()) {
      lineInsets = JBUI.CurrentTheme.Popup.separatorInsets();
      setBorder(JBUI.Borders.empty());
      setFont(RelativeFont.BOLD.derive(JBFont.small()));
    } else {
      lineInsets = JBUI.insets(getVgap(), getHgap(), getVgap(), getHgap());
    }
  }

  public void setHideLine(boolean hideLine) {
    myHideLine = hideLine;
  }

  @Override
  protected Dimension getPreferredElementSize() {
    Dimension size;
    if (getCaption() == null) {
      size = new Dimension(Math.max(myPrefWidth, 0), 0);
    }
    else {
      size = getLabelSize(myLabelInsets);
    }
    if (!myHideLine) size.height += lineInsets.height() + 1;

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
      int lineHeight = lineInsets.height() + 1;
      bounds.y += lineHeight;
      bounds.height -= lineHeight;
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

  private void paintLine(Graphics g, Rectangle bounds) {
    int x = bounds.x + lineInsets.left;
    int width = bounds.width - lineInsets.width();
    int y = bounds.y + lineInsets.top;
    FILL.paint((Graphics2D)g, x, y, width, 1, null);
  }
}
