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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.Gray;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.tabs.TabsUtil;
import com.intellij.ui.tabs.impl.singleRow.ScrollableSingleRowLayout;
import com.intellij.ui.tabs.impl.singleRow.SingleRowLayout;
import com.intellij.ui.tabs.impl.table.TableLayout;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * @author pegov
 */
public class JBEditorTabs extends JBTabsImpl {
  private static final String TABS_ALPHABETICAL_KEY = "tabs.alphabetical";

  public JBEditorTabs(@Nullable Project project, ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
  }

  @Override
  protected SingleRowLayout createSingleRowLayout() {
    if (ApplicationManager.getApplication().isInternal()) {
      return new ScrollableSingleRowLayout(this);
    }
    return super.createSingleRowLayout();
  }

  @Override
  public boolean isEditorTabs() {
    return true;
  }

  @Override
  protected void paintFirstGhost(Graphics2D g2d) {
  }

  @Override
  protected void paintLastGhost(Graphics2D g2d) {
  }

  @Override
  public boolean isGhostsAlwaysVisible() {
    return false;
  }

  @Override
  public boolean useSmallLabels() {
    return SystemInfo.isMac;
  }

  @Override
  public boolean useBoldLabels() {
    return false; // SystemInfo.isMac;
  }

  @Override
  public boolean hasUnderline() {
    return true;
  }

  protected void doPaintInactive(Graphics2D g2d,
                                 boolean leftGhostExists,
                                 TabLabel label,
                                 Rectangle effectiveBounds,
                                 boolean rightGhostExists) {
    Insets insets = getTabsBorder().getEffectiveBorder();

    int _x = effectiveBounds.x + insets.left;
    int _y = effectiveBounds.y + insets.top;
    int _width = effectiveBounds.width - insets.left - insets.right + (getTabsPosition() == JBTabsPosition.right ? 1 : 0);
    int _height = effectiveBounds.height - insets.top - insets.bottom;


    if ((!isSingleRow() /* for multiline */) || (isSingleRow() && isHorizontalTabs()))  {
      if (isSingleRow() && getPosition() == JBTabsPosition.bottom) {
        _y += TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT;
      } else {
        if (isSingleRow()) {
          _height -= TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT;
        } else {
          TabInfo info = label.getInfo();
          if (((TableLayout)getEffectiveLayout()).isLastRow(info)) {
            _height -= TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT;
          }
        }
      }
    }

    Color tabColor = label.getInfo().getTabColor();
    if (tabColor != null) {
      //g2d.setPaint(new LinearGradientPaint(_x, _y, _x, _y + effectiveBounds.height, new float[] {.3f, .6f, 1f}, new Color[] {new SameColor(170), new SameColor(150), new SameColor(90)}));
      g2d.setPaint(new GradientPaint(_x, _y, Gray._200, _x, _y + effectiveBounds.height, Gray._130));
      g2d.fillRect(_x, _y, _width, _height);

      g2d.setColor(ColorUtil.toAlpha(tabColor, 150));
      g2d.fillRect(_x, _y, _width, _height);
    } else {
      g2d.setPaint(new GradientPaint(_x, _y, Gray._255.withAlpha(180), _x, _y + effectiveBounds.height, Gray._255.withAlpha(100)));
      g2d.fillRect(_x, _y, _width, _height);
    }

    g2d.setColor(Gray._255.withAlpha(100));
    g2d.drawRect(_x, _y, _width - 1, _height - 1);
  }

  public static boolean isAlphabeticalMode() {
    return Registry.is(TABS_ALPHABETICAL_KEY);
  }

  public static void setAlphabeticalMode(boolean on) {
    Registry.get(TABS_ALPHABETICAL_KEY).setValue(on);
  }

  private static Color multiplyColor(Color c) {
    return new Color(c.getRed() * c.getRed() / 255, c.getGreen() * c.getGreen() / 255, c.getBlue() * c.getBlue() / 255);
  }

  @Override
  protected void doPaintBackground(Graphics2D g2d, Rectangle clip) {
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
      rectangle = new Rectangle(insets.left, maxOffset, getWidth(),
                                r2.height - maxOffset - insets.top - insets.bottom);
    } else {
      int y = r2.y + insets.top;
      int height = maxLength - insets.top - insets.bottom;
      if (getTabsPosition() == JBTabsPosition.bottom) {
        y = r2.height - height - insets.top + TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT;
      } else {
        height -= TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT;
      }

      rectangle = new Rectangle(maxOffset, y, r2.width - maxOffset - insets.left - insets.right, height);
    }

    g2d.setPaint(new GradientPaint(rectangle.x, rectangle.y, new Color(255, 255, 255, 160),
                                   rectangle.x, rectangle.y + rectangle.height, new Color(255, 255, 255, 120)));
    g2d.fillRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height + (vertical ? 1 : 0));

    if (!vertical) {
      g2d.setColor(Gray._210);
      g2d.drawLine(rectangle.x, rectangle.y, rectangle.x + rectangle.width, rectangle.y);
    }
  }

  protected void paintSelectionAndBorder(Graphics2D g2d) {
    if (getSelectedInfo() == null) return;

    TabLabel label = getSelectedLabel();
    Rectangle r = label.getBounds();

    ShapeInfo selectedShape = _computeSelectedLabelShape();

    Insets insets = getTabsBorder().getEffectiveBorder();
    Insets i = selectedShape.path.transformInsets(insets);

    int _x = r.x;
    int _y = r.y;
    int _height = r.height;

    if (getPosition() == JBTabsPosition.left || getPosition() == JBTabsPosition.right) {
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

    Color tabColor = label.getInfo().getTabColor();
    if (tabColor != null) {
      g2d.setColor(multiplyColor(tabColor));
      g2d.fill(selectedShape.fillPath.getShape());

      g2d.setPaint(new GradientPaint(_x, _y, Gray._255.withAlpha(150), _x, _y + _height, Gray._255.withAlpha(0)));
    } else {
      g2d.setPaint(new GradientPaint(_x, _y, Gray._255, _x, _y + _height, Gray._230));
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

    if (!isHorizontalTabs()) {
      // side shadow
      g2d.setColor(Gray._0.withAlpha(30));
      g2d.draw(selectedShape.labelPath
                 .transformLine(selectedShape.labelPath.getMaxX() + selectedShape.labelPath.deltaX(1), selectedShape.labelPath.getY() +
                                                                                                       selectedShape.labelPath.deltaY(1),
                                selectedShape.labelPath.getMaxX() + selectedShape.labelPath.deltaX(1), selectedShape.labelPath.getMaxY() -
                                                                                                       selectedShape.labelPath.deltaY(4)));

      boolean horizontal = getPosition() == JBTabsPosition.top || getPosition() == JBTabsPosition.bottom;

      g2d.draw(selectedShape.labelPath
                 .transformLine(selectedShape.labelPath.getX() - selectedShape.labelPath.deltaX(horizontal ? 2 : 1),
                                selectedShape.labelPath.getY() +
                                selectedShape.labelPath.deltaY(1),
                                selectedShape.labelPath.getX() - selectedShape.labelPath.deltaX(horizontal ? 2 : 1),
                                selectedShape.labelPath.getMaxY() -
                                selectedShape.labelPath.deltaY(4)));
    }

    g2d.setColor(new Color(0, 0, 0, 50));
    g2d.draw(selectedShape.labelPath.transformLine(i.left, selectedShape.labelPath.getMaxY(),
                                                   selectedShape.path.getMaxX(),
                                                   selectedShape.labelPath.getMaxY()));
  }

  @Override
  public Color getBackground() {
    return Gray._142;
  }

  protected ShapeInfo _computeSelectedLabelShape() {
    final ShapeInfo shape = new ShapeInfo();

    shape.path = getEffectiveLayout().createShapeTransform(getSize());
    shape.insets = shape.path.transformInsets(getLayoutInsets());
    shape.labelPath = shape.path.createTransform(getSelectedLabel().getBounds());

    shape.labelBottomY = shape.labelPath.getMaxY() - shape.labelPath.deltaY(TabsUtil.ACTIVE_TAB_UNDERLINE_HEIGHT - 1);
    shape.labelTopY =
      shape.labelPath.getY() + (getPosition() == JBTabsPosition.top || getPosition() == JBTabsPosition.bottom ? shape.labelPath.deltaY(1) : 0) ;
    shape.labelLeftX = shape.labelPath.getX() + (getPosition() == JBTabsPosition.top || getPosition() == JBTabsPosition.bottom ? 0 : shape.labelPath.deltaX(
      1));
    shape.labelRightX = shape.labelPath.getMaxX() - shape.labelPath.deltaX(1);

    int leftX = shape.insets.left + (getPosition() == JBTabsPosition.top || getPosition() == JBTabsPosition.bottom ? 0 : shape.labelPath.deltaX(1));

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
}
