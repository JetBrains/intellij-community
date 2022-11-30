// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.openapi.util.text.Strings;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtilities;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import java.awt.*;

class AbstractButtonLayout {

  public final Rectangle iconRect = new Rectangle();
  public final Rectangle textRect = new Rectangle();

  private final AbstractButton button;
  private final Dimension size;
  private final boolean removeInsetsBeforeLayout;
  private final String text;
  private final FontMetrics fontMetrics;

  /**
   * @param removeInsetsBeforeLayout remove insets before layout. A very strange parameter, looks like a hack because of the following bug:
   *                                 CheckBox.borderInsets (RadioButton as well) property ignores top/bottom offsets while vertical align.
   *                                 Normally should be always true and removed later
   */
  AbstractButtonLayout(@NotNull AbstractButton button, @NotNull Dimension size, boolean removeInsetsBeforeLayout, Icon defaultIcon) {
    this.button = button;
    this.size = size;
    this.removeInsetsBeforeLayout = removeInsetsBeforeLayout;
    fontMetrics = button.getFontMetrics(button.getFont());

    Rectangle viewRect = new Rectangle(size);
    if (removeInsetsBeforeLayout) {
      JBInsets.removeFrom(viewRect, button.getInsets());
    }

    text = SwingUtilities.layoutCompoundLabel(
      button, fontMetrics, button.getText(), defaultIcon,
      button.getVerticalAlignment(), button.getHorizontalAlignment(),
      button.getVerticalTextPosition(), button.getHorizontalTextPosition(),
      viewRect, iconRect, textRect,
      button.getText() == null ? 0 : button.getIconTextGap());
  }

  public void paint(Graphics g, Color disabledTextColor, int mnemonicIndex) {
    if (button.isOpaque()) {
      g.setColor(button.getBackground());
      g.fillRect(0, 0, size.width, size.height);
    }

    drawText(g, disabledTextColor, mnemonicIndex);
  }

  public @NotNull Dimension getPreferredSize() {
    Insets insets = button.getInsets();
    Rectangle iconRectResult;
    // todo a strange logic that should be revised together with removeInsetsBeforeLayout
    // The following code looks more logical
    /*
    Rectangle rect = iconRect.union(textRect);
    JBInsets.addTo(rect, button.getInsets());
    return new Dimension(rect.width, rect.height);
    */
    if (removeInsetsBeforeLayout) {
      iconRectResult = iconRect.getBounds();
      JBInsets.addTo(iconRectResult, insets);
    } else {
      iconRectResult = iconRect;
    }
    Rectangle textRectResult = textRect.getBounds();
    JBInsets.addTo(textRectResult, insets);
    Rectangle rect = iconRectResult.union(textRectResult);
    return new Dimension(rect.width, rect.height);
  }

  public int getBaseline() {
    if (Strings.isEmpty(button.getText())) {
      return -1;
    }
    return getBaseline(textRect.y, textRect.width, textRect.height);
  }

  private int getBaseline(int y, int w, int h) {
    View view = (View)button.getClientProperty(BasicHTML.propertyKey);
    if (view == null) {
      return y + fontMetrics.getAscent();
    }
    int baseline = BasicHTML.getHTMLBaseline(view, w, h);
    return baseline < 0 ? baseline : y + baseline;
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
