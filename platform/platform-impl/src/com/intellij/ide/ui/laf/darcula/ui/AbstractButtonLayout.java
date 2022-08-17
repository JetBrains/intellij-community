// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtilities;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;

class AbstractButtonLayout {

  public final Rectangle iconRect = new Rectangle();
  public final Rectangle textRect = new Rectangle();

  private final AbstractButton button;
  private final String text;
  private final FontMetrics fontMetrics;
  private final Dimension size;

  /**
   * @param removeInsetsBeforeLayout remove insets before layout. A very strange parameter, looks like a hack because of the following bug:
   *                                 CheckBox.borderInsets (RadioButton as well) property ignores top/bottom offsets while vertical align.
   *                                 Normally should be always true and removed later
   */
  AbstractButtonLayout(AbstractButton button, boolean removeInsetsBeforeLayout, Icon defaultIcon) {
    this.button = button;
    size = button.getSize();
    fontMetrics = button.getFontMetrics(button.getFont());

    Rectangle viewRect = new Rectangle(button.getSize());
    if (removeInsetsBeforeLayout) {
      JBInsets.removeFrom(viewRect, button.getInsets());
    }

    text = SwingUtilities.layoutCompoundLabel(
      button, fontMetrics, button.getText(), defaultIcon,
      button.getVerticalAlignment(), button.getHorizontalAlignment(),
      button.getVerticalTextPosition(), button.getHorizontalTextPosition(),
      viewRect, iconRect, textRect, button.getIconTextGap());
  }

  public void paint(Graphics g, Color disabledTextColor, int mnemonicIndex) {
    if (button.isOpaque()) {
      g.setColor(button.getBackground());
      g.fillRect(0, 0, size.width, size.height);
    }

    drawText(g, disabledTextColor, mnemonicIndex);
  }

  private void drawText(Graphics g, Color disabledTextColor, int mnemonicIndex) {
    if (text != null) {
      View v = (View)button.getClientProperty(BasicHTML.propertyKey);
      if (v != null) {
        v.paint(g, textRect);
      }
      else {
        g.setColor(button.isEnabled() ? button.getForeground() : disabledTextColor);
        UIUtilities.drawStringUnderlineCharAt(button, g, text, mnemonicIndex, textRect.x, textRect.y + fontMetrics.getAscent());
      }
    }
  }
}
