/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
public class DefaultEditorTabsPainter implements JBEditorTabsPainter {

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
    g.setPaint(UIUtil.getGradientPaint(x, shadowTop, new Color(0, 0, 0, 0),
                                   x, shadowBottom, new Color(0, 0, 0, 30)));
    g.fillRect(x, shadowTop, w, ACTIVE_TAB_SHADOW_HEIGHT);
  }

  @Override
  public void doPaintBackground(Graphics2D g, Rectangle clip, boolean vertical, Rectangle rectangle) {
    g.setColor(UIUtil.getPanelBackground());
    g.fill(clip);

    g.setColor(new Color(0, 0, 0, 80));
    g.fill(clip);

    final int x = rectangle.x;
    final int y = rectangle.y;
    g.setPaint(UIUtil.getGradientPaint(x, y, new Color(255, 255, 255, 160),
                                 x, rectangle.y + rectangle.height, new Color(255, 255, 255, 120)));
    g.fillRect(x, rectangle.y, rectangle.width, rectangle.height + (vertical ? 1 : 0));

    if (!vertical) {
      g.setColor(Gray._210);
      g.drawLine(x, rectangle.y, x + rectangle.width, rectangle.y);

      drawShadow(g, rectangle.x, rectangle.width, rectangle.y + rectangle.height);
    }
  }

  private static Color multiplyColor(Color c) {
    return new Color(c.getRed() * c.getRed() / 255, c.getGreen() * c.getGreen() / 255, c.getBlue() * c.getBlue() / 255);
  }

  public void paintSelectionAndBorder(Graphics2D g2d,
                                       Rectangle rect,
                                       JBTabsImpl.ShapeInfo selectedShape,
                                       Insets insets,
                                       Color tabColor,
                                       boolean horizontalTabs) {
    Insets i = selectedShape.path.transformInsets(insets);
    int _x = rect.x;
    int _y = rect.y;
    int _height = rect.height;
    if (!horizontalTabs) {
      g2d.setColor(new Color(0, 0, 0, 45));
      g2d.draw(
        selectedShape.labelPath.transformLine(i.left, selectedShape.labelPath.getMaxY()
                                                      - selectedShape.labelPath.deltaY(4), selectedShape.path.getMaxX(),
                                              selectedShape.labelPath.getMaxY() - selectedShape.labelPath.deltaY(4)));

      g2d.setColor(new Color(0, 0, 0, 15));
      g2d.draw(
        selectedShape.labelPath.transformLine(i.left, selectedShape.labelPath.getMaxY()
                                                      - selectedShape.labelPath.deltaY(5), selectedShape.path.getMaxX(),
                                              selectedShape.labelPath.getMaxY() - selectedShape.labelPath.deltaY(5)));
    }

    if (tabColor != null) {
      g2d.setColor(multiplyColor(tabColor));
      g2d.fill(selectedShape.fillPath.getShape());

      g2d.setPaint(UIUtil.getGradientPaint(_x, _y, Gray._255.withAlpha(150), _x, _y + _height, Gray._255.withAlpha(0)));
    } else {
      g2d.setPaint(UIUtil.getGradientPaint(_x, _y, Gray._255, _x, _y + _height, Gray._230));
    }

    g2d.fill(selectedShape.fillPath.getShape());

    g2d.setColor(Gray._255.withAlpha(180));
    g2d.draw(selectedShape.fillPath.getShape());

    // fix right side due to swing stupidity (fill & draw will occupy different shapes)
    g2d.draw(selectedShape.labelPath
               .transformLine(selectedShape.labelPath.getMaxX() - selectedShape.labelPath.deltaX(1), selectedShape.labelPath.getY() +
                                                                                                     selectedShape.labelPath.deltaY(1),
                              selectedShape.labelPath.getMaxX() - selectedShape.labelPath.deltaX(1), selectedShape.labelPath.getMaxY() -
                                                                                                     selectedShape.labelPath.deltaY(4)));

    if (!horizontalTabs) {
      // side shadow
      g2d.setColor(Gray._0.withAlpha(30));
      g2d.draw(selectedShape.labelPath
                 .transformLine(selectedShape.labelPath.getMaxX() + selectedShape.labelPath.deltaX(1), selectedShape.labelPath.getY() +
                                                                                                       selectedShape.labelPath.deltaY(1),
                                selectedShape.labelPath.getMaxX() + selectedShape.labelPath.deltaX(1), selectedShape.labelPath.getMaxY() -
                                                                                                       selectedShape.labelPath.deltaY(4)));


      g2d.draw(selectedShape.labelPath
                 .transformLine(selectedShape.labelPath.getX() - selectedShape.labelPath.deltaX(horizontalTabs ? 2 : 1),
                                selectedShape.labelPath.getY() +
                                selectedShape.labelPath.deltaY(1),
                                selectedShape.labelPath.getX() - selectedShape.labelPath.deltaX(horizontalTabs ? 2 : 1),
                                selectedShape.labelPath.getMaxY() -
                                selectedShape.labelPath.deltaY(4)));
    }

    g2d.setColor(new Color(0, 0, 0, 50));
    g2d.draw(selectedShape.labelPath.transformLine(i.left, selectedShape.labelPath.getMaxY(),
                                                   selectedShape.path.getMaxX(),
                                                   selectedShape.labelPath.getMaxY()));
  }

  @Override
  public Color getBackgroundColor() {
    return Gray._142;
  }
}
