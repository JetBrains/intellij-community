/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.tabs.impl;

import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DefaultEditorTabsPainter extends JBEditorTabsPainter {

  private static final int ACTIVE_TAB_SHADOW_HEIGHT = 3;

  @Override
  public void doPaintInactive(Graphics2D g2d,
                              Rectangle effectiveBounds,
                              int x,
                              int y,
                              int w,
                              int h,
                              Color tabColor,
                              int row,
                              int column,
                              boolean vertical) {
    if (tabColor != null) {
      g2d.setPaint(UIUtil.getGradientPaint(x, y, Gray._200, x, y + effectiveBounds.height, Gray._130));
      g2d.fillRect(x, y, w, h);

      g2d.setColor(ColorUtil.toAlpha(tabColor, 150));
      g2d.fillRect(x, y, w, h);
    } else {
      g2d.setPaint(UIUtil.getGradientPaint(x, y, Gray._255.withAlpha(180), x, y + effectiveBounds.height, Gray._255.withAlpha(100)));
      g2d.fillRect(x, y, w, h);
    }


    // Push top row under the navbar or toolbar and have a blink over previous row shadow for 2nd and subsequent rows.
    if (row == 0) {
      g2d.setColor(Gray._200.withAlpha(200));
    }
    else {
      g2d.setColor(Gray._255.withAlpha(100));
    }

    g2d.drawLine(x, y, x + w - 1, y);

    if (!vertical) {
      drawShadow(g2d, x, w, y + h);
    }
  }

  private static void drawShadow(Graphics2D g, int x, int w, int shadowBottom) {
    int shadowTop = shadowBottom - ACTIVE_TAB_SHADOW_HEIGHT;
    g.setPaint(UIUtil.getGradientPaint(x, shadowTop, Gray.TRANSPARENT,
                                   x, shadowBottom, Gray._0.withAlpha(30)));
    g.fillRect(x, shadowTop, w, ACTIVE_TAB_SHADOW_HEIGHT);
  }

  @Override
  public void doPaintBackground(Graphics2D g, Rectangle clip, boolean vertical, Rectangle rectangle) {
    g.setColor(UIUtil.getPanelBackground());
    g.fill(clip);

    g.setColor(Gray._0.withAlpha(80));
    g.fill(clip);

    final int x = rectangle.x;
    final int y = rectangle.y;
    g.setPaint(UIUtil.getGradientPaint(x, y, Gray._255.withAlpha(160),
                                 x, rectangle.y + rectangle.height, Gray._255.withAlpha(120)));
    g.fillRect(x, rectangle.y, rectangle.width, rectangle.height + (vertical ? 1 : 0));

    if (!vertical) {
      g.setColor(Gray._210);
      g.drawLine(x, rectangle.y, x + rectangle.width, rectangle.y);

      drawShadow(g, rectangle.x, rectangle.width, rectangle.y + rectangle.height);
    }
  }

  private static Color multiplyColor(Color c) {
    //noinspection UseJBColor
    return new Color(c.getRed() * c.getRed() / 255, c.getGreen() * c.getGreen() / 255, c.getBlue() * c.getBlue() / 255);
  }

  public void fillSelectionAndBorder(Graphics2D g, JBTabsImpl.ShapeInfo selectedShape, Color tabColor, int x, int y, int height) {
    if (tabColor != null) {
      g.setColor(multiplyColor(tabColor));
      g.fill(selectedShape.fillPath.getShape());

      g.setPaint(UIUtil.getGradientPaint(x, y, Gray._255.withAlpha(150), x, y + height, Gray._255.withAlpha(0)));
    } else {
      g.setPaint(UIUtil.getGradientPaint(x, y, Gray._255, x, y + height, Gray._230));
    }

    g.fill(selectedShape.fillPath.getShape());

    g.setColor(Gray._255.withAlpha(180));
    g.draw(selectedShape.fillPath.getShape());

    // fix right side due to swing stupidity (fill & draw will occupy different shapes)
    g.draw(selectedShape.labelPath
               .transformLine(selectedShape.labelPath.getMaxX() - selectedShape.labelPath.deltaX(1), selectedShape.labelPath.getY() +
                                                                                                     selectedShape.labelPath.deltaY(1),
                              selectedShape.labelPath.getMaxX() - selectedShape.labelPath.deltaX(1), selectedShape.labelPath.getMaxY() -
                                                                                                     selectedShape.labelPath.deltaY(4)));
  }

  @Override
  public Color getBackgroundColor() {
    return Gray._142;
  }
}
