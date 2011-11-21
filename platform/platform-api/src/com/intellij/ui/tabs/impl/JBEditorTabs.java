/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.SameColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.util.List;

/**
 * @author pegov
 */
public class JBEditorTabs extends JBTabsImpl {

  public JBEditorTabs(@Nullable Project project, ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
  }

  @Override
  protected void paintFirstGhost(Graphics2D g2d) {
    if (!isNewTabsActive()) {
      super.paintFirstGhost(g2d);
    }
  }

  @Override
  protected void paintLastGhost(Graphics2D g2d) {
    if (!isNewTabsActive()) {
      super.paintLastGhost(g2d);
    }
  }
  
  public boolean isGhostsAlwaysVisible() {
    return super.isGhostsAlwaysVisible() && !isEditorTabs();
  }

  protected void doPaintInactive(Graphics2D g2d,
                                 boolean leftGhostExists,
                                 TabLabel label,
                                 Rectangle effectiveBounds,
                                 boolean rightGhostExists) {
    if (!isNewTabsActive()) {
      super.doPaintInactive(g2d, leftGhostExists, label, effectiveBounds, rightGhostExists);
      return;
    }

    Insets insets = getTabsBorder().getEffectiveBorder();

    int _x = effectiveBounds.x + insets.left;
    int _y = effectiveBounds.y + insets.top;
    int _width = effectiveBounds.width - insets.left - insets.right;
    int _height = effectiveBounds.height - insets.top - insets.bottom;
    
    
    Color tabColor = label.getInfo().getTabColor();
    if (tabColor != null) {
      g2d.setPaint(new GradientPaint(_x, _y, new SameColor(200), _x, _y + effectiveBounds.height, new SameColor(140)));
      g2d.fillRect(_x, _y, _width, _height);

      g2d.setColor(new Color(tabColor.getRed(), tabColor.getGreen(), tabColor.getBlue(), 110));
      g2d.fillRect(_x, _y, _width, _height);
    } else {
      g2d.setPaint(new GradientPaint(_x, _y, new Color(255, 255, 255, 140), _x, _y + effectiveBounds.height, new Color(255, 255, 255, 90)));
      g2d.fillRect(_x, _y, _width, _height);
    }

    g2d.setColor(new Color(255, 255, 255, 100));
    g2d.drawRect(_x, _y, _width - 1, _height - 1);
  }
  
  private static Color multiplyColor(Color c) {
    return new Color(c.getRed() * c.getRed() / 255, c.getGreen() * c.getGreen() / 255, c.getBlue() * c.getBlue() / 255);
  }

  public boolean isNewTabsActive() {
    return Registry.is("editor.use.new.tabs");
  }

  @Override
  protected void doPaintBackground(Graphics2D g2d, Rectangle clip) {
    if (isNewTabsActive() && isEditorTabs()) {
      g2d.setColor(UIUtil.getPanelBackground());
      g2d.fill(clip);
      
      g2d.setColor(new Color(0, 0, 0, 80));
      g2d.fill(clip);
      
      List<TabInfo> visibleInfos = getVisibleInfos();
      
      final boolean vertical = getTabsPosition() == JBTabsPosition.left || getTabsPosition() == JBTabsPosition.right;
      
      Insets insets = getTabsBorder().getEffectiveBorder();

      int maxOffset = 0;
      int maxLength = 0;
      
      for (int i = visibleInfos.size() - 1; i >= 0; i--) {
        TabInfo visibleInfo = visibleInfos.get(i);
        TabLabel tabLabel = myInfo2Label.get(visibleInfo);
        Rectangle r = tabLabel.getBounds();
        if (r.width == 0 || r.height == 0) continue;
        maxOffset = vertical ? r.y + r.height : r.x + r.width;
        maxLength = vertical ? r.width : r.height;
        break;
      }
      
      maxOffset++;
      
      Rectangle r2 = getBounds();

      Rectangle rectangle;
      if (vertical) {
        rectangle = new Rectangle(insets.left, maxOffset, maxLength - insets.left - insets.right,
                                  r2.height - maxOffset - insets.top - insets.bottom);
      } else {
        int y = r2.y + insets.top;
        int height = maxLength - insets.top - insets.bottom;
        if (getTabsPosition() == JBTabsPosition.bottom) {
          y = r2.height - height - insets.top;
        }

        rectangle = new Rectangle(maxOffset, y, r2.width - maxOffset - insets.left - insets.right, height);
      }

      g2d.setPaint(new GradientPaint(rectangle.x, rectangle.y, new Color(255, 255, 255, 160), 
                                     rectangle.x, rectangle.y + rectangle.height, new Color(255, 255, 255, 120)));
      g2d.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height + (vertical ? 1 : 0));
      
      if (!vertical) {
        g2d.setColor(new SameColor(210));
        g2d.drawLine(rectangle.x, rectangle.y, rectangle.x + rectangle.width, rectangle.y);
      }
    } else {
      super.doPaintBackground(g2d, clip);
    }
  }

  protected void paintSelectionAndBorder(Graphics2D g2d) {
    if (!isNewTabsActive()) {
      super.paintSelectionAndBorder(g2d);
      return;
    }
    
    if (getSelectedInfo() == null) return;

    TabLabel label = getSelectedLabel();
    Rectangle r = label.getBounds();

    Insets insets = getTabsBorder().getEffectiveBorder();

    int _x = r.x + insets.left;
    int _y = r.y + insets.top;
    int _width = r.width - insets.left - insets.right;
    int _height = r.height - insets.top - insets.bottom;

    Color tabColor = label.getInfo().getTabColor();
    if (tabColor != null) {
      g2d.setColor(multiplyColor(tabColor));
      g2d.fillRect(_x, _y, _width, _height);
      
      g2d.setPaint(new GradientPaint(_x, _y, new Color(255, 255, 255, 150), _x, _y + _height, new Color(255, 255, 255, 0)));
    } else {
      g2d.setPaint(new GradientPaint(_x, _y, new SameColor(255), _x, _y + _height, new SameColor(210)));
    }

    g2d.fillRect(_x, _y, _width, _height);
    
    g2d.setColor(new Color(255, 255, 255, 180));
    g2d.drawRect(_x, _y, _width - 1, _height - 1);
  }
  
  private static Shape createColorShape(int x, int y, int height) {
    GeneralPath shape = new GeneralPath();
    shape.moveTo(x, y);
    shape.lineTo(x + height / 2, y);
    shape.lineTo(x, y + height / 2);
    shape.closePath();
    return shape;
  }
  
  private static void drawColorCornerAt(Graphics2D g2d, int _x, int _y, int _height, Color tabColor) {
    g2d.setColor(multiplyColor(tabColor));
    Shape shape = createColorShape(_x + 1, _y + 1, _height);
    g2d.fill(shape);
    g2d.setPaint(new GradientPaint(_x + 1, _y + 1, new Color(0, 0, 0, 50), _x + 1, _y + 1 + _height / 5, new Color(0, 0, 0, 0)));
    g2d.fill(shape);

    g2d.setColor(new Color(0, 0, 0, 100));
    g2d.drawLine(_x + 1, _y + 1, _x + _height / 2, _y + 1);
    g2d.setColor(new Color(0, 0, 0, 75));
    g2d.drawLine(_x + 1, _y + 1, _x + 1, _y + _height / 2);
    g2d.setColor(new Color(255, 255, 255, 130));
    g2d.drawLine(_x + _height / 2 + 1, _y + 1, _x + 1, _y + _height / 2 + 1);
  }

  @Override
  public Color getBackground() {
    return new SameColor(142);
  }
}
