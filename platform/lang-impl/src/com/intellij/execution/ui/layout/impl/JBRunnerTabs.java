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
package com.intellij.execution.ui.layout.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.ui.tabs.UiDecorator;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.ui.tabs.impl.TabLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * @author Dennis.Ushakov
 */
public class
  JBRunnerTabs extends JBTabsImpl {
  public JBRunnerTabs(@Nullable Project project, ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
  }

  @Override
  public boolean useSmallLabels() {
    return true;
  }

  @Override
  public boolean hasUnderline() {
    return true;
  }

  @Override
  protected void paintFirstGhost(Graphics2D g2d) {}

  @Override
  protected void paintLastGhost(Graphics2D g2d) {}

  @Override
  protected void doPaintInactive(Graphics2D g2d,
                                 boolean leftGhostExists,
                                 TabLabel label,
                                 Rectangle effectiveBounds,
                                 boolean rightGhostExists, int row, int column) {
    Insets insets = getTabsBorder().getEffectiveBorder();
    final boolean dark = UIUtil.isUnderDarcula();
    int _x = effectiveBounds.x + insets.left;
    int _y = effectiveBounds.y + insets.top + 3;
    int _width = effectiveBounds.width - insets.left - insets.right;
    int _height = effectiveBounds.height - insets.top - insets.bottom - 3;
    _height -= TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT;
    if (dark) {
      g2d.setPaint(UIUtil.getGradientPaint(_x, _y, ColorUtil.shift(UIUtil.getListBackground(), 1.3), _x, _y + effectiveBounds.height, UIUtil.getPanelBackground()));
      g2d.fillRect(_x, _y, _width, _height);

      g2d.setColor(Gray._0.withAlpha(50));
    } else {
      g2d.setPaint(UIUtil.getGradientPaint(_x, _y, new Color(255, 255, 255, 180), _x, _y + effectiveBounds.height, new Color(255, 255, 255, 100)));
      g2d.fillRect(_x, _y, _width, _height);

      g2d.setColor(new Color(255, 255, 255, 100));
      g2d.drawRect(_x, _y, _width - 1, _height - 1);
    }
  }

  @Override
  protected void doPaintBackground(Graphics2D g2d, Rectangle clip) {
    g2d.setColor(UIUtil.getPanelBackground());
    g2d.fill(clip);

    g2d.setColor(new Color(0, 0, 0, 50));
    g2d.fill(clip);

    List<TabInfo> visibleInfos = getVisibleInfos();

    Insets insets = getTabsBorder().getEffectiveBorder();

    int maxOffset = 0;
    int maxLength = 0;

    for (int i = visibleInfos.size() - 1; i >= 0; i--) {
      TabInfo visibleInfo = visibleInfos.get(i);
      TabLabel tabLabel = myInfo2Label.get(visibleInfo);
      Rectangle r = tabLabel.getBounds();
      if (r.width == 0 || r.height == 0) continue;
      maxOffset = r.x + r.width;
      maxLength = r.height;
      break;
    }

    maxOffset++;

    Rectangle r2 = getBounds();

    Rectangle rectangle;
    int y = r2.y + insets.top;
    int height = maxLength - insets.top - insets.bottom;
    height -= TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT;

    rectangle = new Rectangle(maxOffset, y, r2.width - maxOffset - insets.left - insets.right, height);

    g2d.setPaint(UIUtil.getPanelBackground());
    g2d.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
    g2d.fillRect(0, 0, rectangle.x + rectangle.width, 3);
    g2d.fillRect(2, maxLength, getSize().width, getSize().height);
    g2d.drawLine(0, 0, 0, getSize().height);
  }

  @Override
  protected void paintSelectionAndBorder(Graphics2D g2d) {
    if (getSelectedInfo() == null) return;
    final boolean dark = UIUtil.isUnderDarcula();
    final Color col = dark ? ColorUtil.shift(UIUtil.getListBackground(), 1.6) : Gray._255;
    final Color panelBg = dark ? ColorUtil.shift(UIUtil.getPanelBackground(), 1.3) : UIUtil.getPanelBackground();

    TabLabel label = getSelectedLabel();
    Rectangle r = label.getBounds();
    r = new Rectangle(r.x, r.y + 3, r.width, r.height - 3);

    ShapeInfo selectedShape = _computeSelectedLabelShape(r);

    Insets insets = getTabsBorder().getEffectiveBorder();
    Insets i = selectedShape.path.transformInsets(insets);

    int _x = r.x;
    int _y = r.y;
    int _height = r.height;

    if (!isHideTabs()) {
      g2d.setPaint(UIUtil.getGradientPaint(_x, _y, col, _x, _y + _height - 3, panelBg));

      g2d.fill(selectedShape.fillPath.getShape());

      g2d.setColor(ColorUtil.toAlpha(col, 180));
      g2d.draw(selectedShape.fillPath.getShape());

      // fix right side due to swing stupidity (fill & draw will occupy different shapes)
      g2d.draw(selectedShape.labelPath
                 .transformLine(selectedShape.labelPath.getMaxX() - selectedShape.labelPath.deltaX(1), selectedShape.labelPath.getY() +
                                                                                                       selectedShape.labelPath.deltaY(1),
                                selectedShape.labelPath.getMaxX() - selectedShape.labelPath.deltaX(1), selectedShape.labelPath.getMaxY() -
                                                                                                       selectedShape.labelPath.deltaY(4)));
    }
    if (UIUtil.isUnderDarcula()) return;
    g2d.setColor(panelBg);
    g2d.fillRect(2, selectedShape.labelPath.getMaxY() - 2, selectedShape.path.getMaxX() - 2, 3);
    g2d.drawLine(1, selectedShape.labelPath.getMaxY(), 1, getHeight() - 1);
    g2d.drawLine(selectedShape.path.getMaxX() - 1, selectedShape.labelPath.getMaxY() - 4,
                 selectedShape.path.getMaxX() - 1, getHeight() - 1);

    if (isHideTabs()) return;
    g2d.setColor(Gray._0.withAlpha(50));
    g2d.drawLine(1, selectedShape.labelPath.getMaxY(), 1, getHeight() - 1);
    g2d.drawLine(selectedShape.path.getMaxX() - 1, selectedShape.labelPath.getMaxY() - 4,
                 selectedShape.path.getMaxX() - 1, getHeight() - 1);
  }

  @Override
  public Color getBackground() {
    return UIUtil.isUnderDarcula() ? UIUtil.getPanelBackground() : Gray._142;
  }

  protected ShapeInfo _computeSelectedLabelShape(Rectangle r) {
    final ShapeInfo shape = new ShapeInfo();

    shape.path = getEffectiveLayout().createShapeTransform(getSize());
    shape.insets = shape.path.transformInsets(getLayoutInsets());
    shape.labelPath = shape.path.createTransform(r);

    shape.labelBottomY = shape.labelPath.getMaxY() - shape.labelPath.deltaY(TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT - 1);
    shape.labelTopY = shape.labelPath.getY() + shape.labelPath.deltaY(1);
    shape.labelLeftX = shape.labelPath.getX();
    shape.labelRightX = shape.labelPath.getMaxX() - shape.labelPath.deltaX(1);

    int leftX = shape.insets.left;

    shape.path.moveTo(leftX, shape.labelBottomY);
    shape.path.lineTo(shape.labelLeftX, shape.labelBottomY);
    shape.path.lineTo(shape.labelLeftX, shape.labelTopY);
    shape.path.lineTo(shape.labelRightX, shape.labelTopY);
    shape.path.lineTo(shape.labelRightX, shape.labelBottomY);

    int lastX = shape.path.getWidth() - shape.path.deltaX(shape.insets.right);

    shape.path.lineTo(lastX, shape.labelBottomY);
    shape.path.lineTo(lastX, shape.labelBottomY + shape.labelPath.deltaY(TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT - 1));
    shape.path.lineTo(leftX, shape.labelBottomY + shape.labelPath.deltaY(TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT - 1));

    shape.path.closePath();
    shape.fillPath = shape.path.copy();

    return shape;
  }

  @Override
  public int getToolbarInset() {
    return 8;
  }

  public boolean shouldAddToGlobal(Point point) {
    final TabLabel label = getSelectedLabel();
    if (label == null || point == null) {
      return true;
    }
    final Rectangle bounds = label.getBounds();
    return point.y <= bounds.y + bounds.height;
  }

  @Override
  public Rectangle layout(JComponent c, Rectangle bounds) {
    if (c instanceof Toolbar) {
      bounds.height -= 5;
      return super.layout(c, bounds);
    }
    if (c instanceof GridImpl) {
      bounds.x -= 1;
      bounds.width += 1;
      if (!isHideTabs()) {
        bounds.y -= 1;
        bounds.height += 1;
      }
    }
    return super.layout(c, bounds);
  }

  @Override
  public void processDropOver(TabInfo over, RelativePoint relativePoint) {
    final Point point = relativePoint.getPoint(getComponent());
    myShowDropLocation = shouldAddToGlobal(point);
    super.processDropOver(over, relativePoint);
    for (Map.Entry<TabInfo, TabLabel> entry : myInfo2Label.entrySet()) {
      final TabLabel label = entry.getValue();
      if (label.getBounds().contains(point) && myDropInfo != entry.getKey()) {
        select(entry.getKey(), false);
        break;
      }
    }
  }

  @Override
  protected TabLabel createTabLabel(TabInfo info) {
    return new MyTabLabel(this, info);
  }

  private static class MyTabLabel extends TabLabel {
    public MyTabLabel(JBTabsImpl tabs, final TabInfo info) {
      super(tabs, info);
    }

    @Override
    public void apply(UiDecorator.UiDecoration decoration) {
      setBorder(new EmptyBorder(5, 5, 7, 5));
    }

    @Override
    public void setTabActionsAutoHide(boolean autoHide) {
      super.setTabActionsAutoHide(autoHide);
      apply(null);
    }

    @Override
    public void setTabActions(ActionGroup group) {
      super.setTabActions(group);
      if (myActionPanel != null) {
        final JComponent wrapper = (JComponent)myActionPanel.getComponent(0);
        wrapper.remove(0);
        wrapper.add(Box.createHorizontalStrut(6), BorderLayout.WEST);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      Dimension result = super.getPreferredSize();
      result.height += TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT;
      return result;
    }
  }
}
