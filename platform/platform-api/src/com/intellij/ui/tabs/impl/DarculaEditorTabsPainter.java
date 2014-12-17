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

import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
class DarculaEditorTabsPainter extends JBEditorTabsPainter {
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
      g2d.setColor(tabColor);
      g2d.fillRect(x, y, w, h);
    } else {
      g2d.setPaint(UIUtil.getControlColor());
      g2d.fillRect(x, y, w, h);
    }

    g2d.setColor(Gray._0.withAlpha(10));
    g2d.drawRect(x, y, w - 1, h - 1);
  }

  @Override
  public void doPaintBackground(Graphics2D g, Rectangle clip, boolean vertical, Rectangle rectangle) {
    g.setColor(UIUtil.getPanelBackground());
    g.fill(clip);

    g.setColor(Gray._0.withAlpha(80));
    g.fill(clip);

    final int x = rectangle.x;
    final int y = rectangle.y;
    final int h = rectangle.height;
    g.setPaint(UIUtil.getGradientPaint(x, y, Gray._78.withAlpha(160), x, y + h, Gray._78.withAlpha(120)));
    final int w = rectangle.width;
    g.fillRect(x, rectangle.y, w, h + (vertical ? 1 : 0));

    if (!vertical) {
      g.setColor(Gray._78);
      g.drawLine(x, rectangle.y, x + w, rectangle.y);
    }
  }


  public void fillSelectionAndBorder(Graphics2D g, JBTabsImpl.ShapeInfo selectedShape, Color tabColor, int x, int y, int height) {
    if (tabColor != null) {
      g.setColor(tabColor);
      g.fill(selectedShape.fillPath.getShape());

      g.setPaint(UIUtil.getGradientPaint(x, y, Gray._255.withAlpha(50), x, y + height, Gray._255.withAlpha(0)));
    } else {
      g.setPaint(UIUtil.getGradientPaint(x, y, Gray._85, x, y + height, Gray._60));
    }

    g.fill(selectedShape.fillPath.getShape());

    g.setColor(Gray._135.withAlpha(90));
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
    //noinspection UseJBColor
    return new Color(0x3C3F41);
  }
}
