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

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
@SuppressWarnings("UseJBColor")
public class DefaultEditorTabsPainter extends JBEditorTabsPainter {

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
    ;
    g2d.setColor(tabColor != null ? tabColor : getDefaultTabColor());
    g2d.fillRect(x, y, w, h);
    g2d.setColor(getInactiveMaskColor());
    g2d.fillRect(x, y, w, h);

    if (!vertical) {
      drawShadowLines(g2d, x, y, w);
    }
  }

  @Override
  public void doPaintBackground(Graphics2D g, Rectangle clip, boolean vertical, Rectangle rectangle) {
    g.setColor(getBackgroundColor());
    g.fill(clip);
    if (!vertical) {
      drawShadowLines(g, rectangle.x, rectangle.y, rectangle.width);
    }
  }

  private void drawShadowLines(Graphics g, int x, int y, int width) {
    g.setColor(ColorUtil.withAlpha(getShadowBaseColor(), .5));
    g.drawLine(x, y, x + width, y);
    g.setColor(ColorUtil.withAlpha(getShadowBaseColor(), .2));
    g.drawLine(x, y+1, x + width, y+1);
  }

  public void fillSelectionAndBorder(Graphics2D g, JBTabsImpl.ShapeInfo selectedShape, Color tabColor, int x, int y, int height) {
    g.setColor(tabColor != null ? tabColor : getDefaultTabColor());
    g.fill(selectedShape.fillPath.getShape());
    g.draw(selectedShape.fillPath.getShape());
  }

  @Override
  public Color getBackgroundColor() {
    return Gray._177;
  }

  protected Color getDefaultTabColor() {
    return Color.WHITE;
  }

  protected Color getInactiveMaskColor() {
    return ColorUtil.withAlpha(new Color(0x262626), .2);
  }

  protected Color getShadowBaseColor() {
    return new Color(0xbababa);
  }
}
