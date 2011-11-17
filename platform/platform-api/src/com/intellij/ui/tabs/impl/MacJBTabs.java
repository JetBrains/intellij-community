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
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.Gray;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Line2D;
import java.util.List;

/**
 * @author pegov
 */
public class MacJBTabs extends JBTabsImpl {

  public MacJBTabs(@Nullable Project project, ActionManager actionManager, IdeFocusManager focusManager, @NotNull Disposable parent) {
    super(project, actionManager, focusManager, parent);
  }

  @Override
  protected void paintFirstGhost(Graphics2D g2d) {
    if (!isNewTabsActive()) {
      super.paintFirstGhost(g2d);
      return;
    }
    
    final ShapeTransform path = getEffectiveLayout().createShapeTransform(getSingleRowLayout().myLastSingRowLayout.firstGhost);
    final ShapeTransform shadow = getEffectiveLayout().createShapeTransform(getSingleRowLayout().myLastSingRowLayout.firstGhost);

    int topX = path.getX() + path.deltaX(getCurveArc());
    int topY = path.getY() + path.deltaY(getSelectionTabVShift()) + 1;
    int bottomX = path.getMaxX() - 1 /* + path.deltaX(1) */; 
    int bottomY = path.getMaxY() + path.deltaY(1);

    path.moveTo(topX, topY);

    path.lineTo(bottomX - getArcSize(), topY);
    path.quadTo(bottomX, topY, bottomX, topY + path.deltaY(getArcSize()));

    path.lineTo(bottomX, bottomY);
    path.lineTo(topX, bottomY);

    path.quadTo(topX - path.deltaX(getCurveArc() * 2 - 1), bottomY - path.deltaY(Math.abs(bottomY - topY) / 4), topX,
                bottomY - path.deltaY(Math.abs(bottomY - topY) / 2));

    path.quadTo(topX + path.deltaX(getCurveArc() - 1), topY + path.deltaY(Math.abs(bottomY - topY) / 4), topX, topY);

    path.closePath();

    g2d.setColor(new Color(0, 0, 0, 10));
    g2d.fill(path.getShape());

    g2d.setColor(new Color(120, 120, 120, 120));
    g2d.draw(path.getShape());

    g2d.setColor(new Color(255, 255, 255, 80));
    g2d.drawLine(topX + path.deltaX(1), topY + path.deltaY(1), bottomX - path.deltaX(getArcSize()), topY + path.deltaY(1));

    // TODO: SHADOW!!!
  }

  @Override
  protected void paintLastGhost(Graphics2D g2d) {
    if (!isNewTabsActive()) {
      super.paintLastGhost(g2d);
      return;
    }
    
    final ShapeTransform path = getEffectiveLayout().createShapeTransform(getSingleRowLayout().myLastSingRowLayout.lastGhost);

    int topX = path.getX(); //  - path.deltaX(getArcSize());
    int topY = path.getY() + path.deltaY(getSelectionTabVShift()) + 1;
    int bottomX = path.getMaxX() - path.deltaX(getCurveArc());
    int bottomY = path.getMaxY() + path.deltaY(1);

    path.moveTo(topX, topY + getArcSize());
    path.quadTo(topX,  topY,  topX + getArcSize(), topY);
    path.lineTo(bottomX, topY);
    path.quadTo(bottomX - getCurveArc(), topY + (bottomY - topY) / 4, bottomX, topY + (bottomY - topY) / 2);
    path.quadTo(bottomX + getCurveArc(), bottomY - (bottomY - topY) / 4, bottomX, bottomY);
    path.lineTo(topX, bottomY);

    path.closePath();

    g2d.setColor(new Color(0, 0, 0, 10));
    g2d.fill(path.getShape());

    g2d.setColor(new Color(120, 120, 120, 120));
    g2d.draw(path.getShape());

    g2d.setColor(new Color(255, 255, 255, 80));
    g2d.drawLine(topX + path.deltaY(getArcSize()), topY + path.deltaY(1), bottomX - path.deltaX(getCurveArc()), topY + path.deltaY(1));
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
    
    List<TabInfo> visibleInfos = getVisibleInfos();
    
    TabInfo tabInfo = label.getInfo();
    int tabIndex = visibleInfos.indexOf(tabInfo);

    final int arc = getArcSize();
    Color topBlickColor = new Color(255, 255, 255, 100);
    Color rightBlockColor = getRightBlockColor();

    final Color tabColor = label.getInfo().getTabColor();
    if (tabColor != null) {
      topBlickColor = tabColor;
    }

    final TabInfo selected = getSelectedInfo();
    final int selectionTabVShift = getSelectionTabVShift();

    LayoutPassInfo lastLayoutPass = getLastLayoutPass();

    final TabInfo prev = lastLayoutPass.getPreviousFor(visibleInfos.get(tabIndex));
    final TabInfo next = lastLayoutPass.getNextFor(visibleInfos.get(tabIndex));

    boolean firstShowing = prev == null;
    if (!firstShowing && !leftGhostExists) {
      firstShowing = getInfoForLabel(prev).getBounds().width == 0;
    }

    boolean lastShowing = next == null;
    if (!lastShowing) {
      lastShowing = getInfoForLabel(next).getBounds().width == 0;
    }

    boolean leftFromSelection = selected != null && tabIndex == visibleInfos.indexOf(selected) - 1;

    final ShapeTransform shape = getEffectiveLayout().createShapeTransform(effectiveBounds);
    final ShapeTransform shadowShape = getEffectiveLayout().createShapeTransform(effectiveBounds);

    int leftX = /*firstShowing ? shape.getX() : */shape.getX() /*- shape.deltaX(arc + 1) */;
    int topY = shape.getY() + shape.deltaY(selectionTabVShift);
    int rigthX = /*!lastShowing && leftFromSelection ? shape.getMaxX() + shape.deltaX(arc + 1) : */shape.getMaxX() - 1;
    int bottomY = shape.getMaxY() + shape.deltaY(1);

    Insets border = getTabsBorder().getEffectiveBorder();

    if (border.left > 0 || leftGhostExists || !firstShowing) {
      shape.moveTo(leftX, bottomY);
      shape.lineTo(leftX, topY + shape.deltaY(arc));
      shape.quadTo(leftX, topY + 1, leftX + shape.deltaX(arc), topY + 1);

      shadowShape.moveTo(leftX - 1, bottomY);
      shadowShape.lineTo(leftX - 1, topY + shadowShape.deltaY(arc));
      shadowShape.quadTo(leftX - 1, topY, leftX - 1 + shadowShape.deltaX(arc), topY);

    } else {
      if (firstShowing) {
        shape.moveTo(leftX, topY + shape.deltaY(getEdgeArcSize()));
        shape.quadTo(leftX, topY + 1, leftX + shape.deltaX(getEdgeArcSize()), topY + 1);
        
        shadowShape.moveTo(leftX - 1, topY + shadowShape.deltaY(getEdgeArcSize()));
        shadowShape.quadTo(leftX - 1, topY, leftX + shadowShape.deltaX(getEdgeArcSize()), topY);
      }
    }

    boolean rightEdge = false;
    if (border.right > 0 || rightGhostExists || !lastShowing || !Boolean.TRUE.equals(label.getClientProperty(STRETCHED_BY_WIDTH))) {
      shape.lineTo(rigthX - shape.deltaX(arc), topY + 1);
      shape.quadTo(rigthX, topY + 1, rigthX, topY + shape.deltaY(arc));
      shape.lineTo(rigthX, bottomY);

      shadowShape.lineTo(rigthX - shadowShape.deltaX(arc), topY);
      shadowShape.quadTo(rigthX + 1, topY, rigthX + 1, topY + shadowShape.deltaY(arc));
      shadowShape.lineTo(rigthX + 1, bottomY);
    } else {
      if (lastShowing) {
        shape.lineTo(rigthX - shape.deltaX(arc), topY + 1);
        shape.quadTo(rigthX, topY, rigthX, topY + shape.deltaY(arc));

        shape.lineTo(rigthX, bottomY);
        rightEdge = true;

        shadowShape.lineTo(rigthX - shadowShape.deltaX(arc), topY);
        shadowShape.quadTo(rigthX + 1, topY, rigthX + 1, topY + shadowShape.deltaY(arc));
        shadowShape.lineTo(rigthX + 1, bottomY);
      }
    }

    if (!rightEdge) {
      shape.lineTo(leftX, bottomY);
      shadowShape.lineTo(leftX, bottomY);
    }

    //g2d.setColor(new Color(69, 128, 6));
    //g2d.fill(shape.getShape());

    // TODO

    final Line2D.Float gradientLine =
      shape.transformLine(0, topY, 0, topY + shape.deltaY((int) (shape.getHeight() / 1.5 )));

    final GradientPaint gp =
      new GradientPaint(gradientLine.x1, gradientLine.y1,
                        shape.transformY1(new Color(0, 0, 0, 15), new Color(0, 0, 0, 30)),
                        gradientLine.x2, gradientLine.y2,
                        shape.transformY1(new Color(0, 0, 0, 30), new Color(0, 0, 0, 15)));
    final Paint old = g2d.getPaint();
    g2d.setPaint(gp);
    g2d.fill(shape.getShape());
    g2d.setPaint(old);
    
    g2d.setColor(tabColor == null ? new Color(255, 255, 255, 30) : new Color(tabColor.getRed(), tabColor.getGreen(), tabColor.getBlue(), 130));
    g2d.fill(shape.getShape());
    
    g2d.setColor(new Color(0, 0, 0, 12));
    g2d.draw(shadowShape.getShape());
    
    g2d.setColor(topBlickColor);
    g2d.draw(
      shape.transformLine(leftX + shape.deltaX(arc), topY + 1 + shape.deltaY(1), rigthX - shape.deltaX(arc - 1) - 1, topY + 1 + shape.deltaY(1)));

    if (!rightEdge) {
      g2d.setColor(rightBlockColor);
      //g2d.draw(shape.transformLine(rigthX - shape.deltaX(1), topY + shape.deltaY(arc - 1), rigthX - shape.deltaX(1), bottomY));
    }

    g2d.setColor(Gray._130);
    g2d.draw(shape.getShape());
  }
  
  public boolean isNewTabsActive() {
    return getPresentation().getTabsPosition() == JBTabsPosition.top && Registry.is("editor.use.new.tabs") && isSingleRow() && !isHideTabs();
  }
  
  protected void paintSelectionAndBorder(Graphics2D g2d) {
    if (!isNewTabsActive()) {
      super.paintSelectionAndBorder(g2d);
      return;
    }
    
    if (getSelectedInfo() == null) return;

    Pair<ShapeInfo, ShapeTransform> pair = computeSelectedLabelShape2();
    final ShapeInfo shapeInfo = pair.getFirst();
    if (!isHideTabs()) {
      g2d.setColor(getBackground());
      g2d.fill(shapeInfo.fillPath.getShape());
    }

    final int alpha;
    int paintTopY = shapeInfo.labelTopY;
    int paintBottomY = shapeInfo.labelBottomY;
    final boolean paintFocused = shouldPaintFocus() && (myFocused || myActivePopup != null);
    Color bgPreFill = null;
    if (paintFocused) {
      final Color bgColor = getActiveTabColor(getActiveTabFillIn());
      if (bgColor == null) {
        shapeInfo.from = UIUtil.getFocusedFillColor();
        shapeInfo.to = UIUtil.getFocusedFillColor();
      }
      else {
        bgPreFill = bgColor;
        alpha = 255;
        paintBottomY = shapeInfo.labelTopY + shapeInfo.labelPath.deltaY(getArcSize() - 2);
        shapeInfo.from = UIUtil.toAlpha(UIUtil.getFocusedFillColor(), alpha);
        shapeInfo.to = UIUtil.toAlpha(getActiveTabFillIn(), alpha);
      }
    }
    else {
      final Color bgColor = getActiveTabColor(getActiveTabFillIn());
      if (isPaintFocus()) {
        if (bgColor == null) {
          alpha = 150;
          shapeInfo.from = UIUtil.toAlpha(UIUtil.getPanelBackground().brighter(), alpha);
          shapeInfo.to = UIUtil.toAlpha(UIUtil.getPanelBackground(), alpha);
        }
        else {
          alpha = 255;
          shapeInfo.from = UIUtil.toAlpha(bgColor, alpha);
          shapeInfo.to = UIUtil.toAlpha(bgColor, alpha);
        }
      }
      else {
        alpha = 255;
        final Color tabColor = getActiveTabColor(null);
        shapeInfo.from = UIUtil.toAlpha(tabColor == null ? Color.white : tabColor, alpha);
        shapeInfo.to = UIUtil.toAlpha(tabColor == null ? Color.white : tabColor, alpha);
      }
    }

    Color tabColor = getActiveTabColor(null);

    if (!isHideTabs()) {
      if (bgPreFill != null) {
        g2d.setColor(bgPreFill);
        g2d.fill(shapeInfo.fillPath.getShape());
      }

      // draw bottom shadow over tabs
      ShapeTransform shadowPath = shapeInfo.path.copy().reset();
      //g2d.setColor(new Color(0, 0, 0, 255));
      //g2d.fill(shadowPath.doRect(shapeInfo.fillPath.getX(), shapeInfo.labelBottomY, shapeInfo.fillPath.getWidth(), 1).getShape());

      g2d.setColor(new Color(0, 0, 0, 25));
      g2d.fill(shadowPath.doRect(shapeInfo.path.getX(), shapeInfo.labelBottomY - 1, shapeInfo.path.getWidth(), 1).getShape());
      
      g2d.setColor(new Color(0, 0, 0, 7));
      g2d.fill(shadowPath.reset().doRect(shapeInfo.fillPath.getX(), shapeInfo.labelBottomY - 2, shapeInfo.fillPath.getWidth(), 1).getShape());

      final Line2D.Float gradientLine =
        shapeInfo.fillPath.transformLine(shapeInfo.fillPath.getX(), paintTopY, shapeInfo.fillPath.getX(), paintBottomY);


      if (tabColor == null) {
        g2d.setPaint(new GradientPaint((float)gradientLine.getX1(), (float)gradientLine.getY1(),
                                       shapeInfo.fillPath.transformY1(Gray._255, Gray._230), (float)gradientLine.getX2(),
                                       (float)gradientLine.getY2(), shapeInfo.fillPath.transformY1(Gray._230, Gray._255)));
      } else {
        tabColor = new Color(tabColor.getRed() * tabColor.getRed() / 275, tabColor.getGreen() * tabColor.getGreen() / 275, tabColor.getBlue() * tabColor.getBlue() / 275);

        g2d.setColor(tabColor);
        g2d.fill(shapeInfo.fillPath.getShape());

        Color from = new Color(255, 255, 255, 100);
        Color to = new Color(255, 255, 255, 0);
        g2d.setPaint(new GradientPaint((float)gradientLine.getX1(), (float)gradientLine.getY1(),
                                       shapeInfo.fillPath.transformY1(from, to), (float)gradientLine.getX2(),
                                       (float)gradientLine.getY2(), shapeInfo.fillPath.transformY1(to, from)));
      }

      g2d.fill(shapeInfo.fillPath.getShape());
      
      g2d.setColor(new Color(255, 255, 255, 200));
      g2d.draw(shapeInfo.labelPath.transformLine(shapeInfo.labelPath.getX() + shapeInfo.labelPath.deltaX(getArcSize()) - 1, 
                                                 paintTopY + 2, shapeInfo.labelPath.getMaxX() - shapeInfo.labelPath.deltaX(getArcSize()) + 1, paintTopY + 2));
      
      g2d.setColor(new Color(0, 0, 0, 12));
      g2d.draw(pair.getSecond().getShape());
    }

    Color borderColor = tabColor == null ? UIUtil.getBoundsColor(paintFocused) : tabColor.darker();
    g2d.setColor(borderColor);

    if (!isHideTabs()) {
      g2d.setColor(Gray._130);
      g2d.draw(shapeInfo.path.getShape());
    }

    paintBorder2(g2d, shapeInfo, tabColor == null ? Gray._230 : tabColor);
  }
  
  protected void paintBorder2(Graphics2D g2d, ShapeInfo shape, final Color borderColor) {
    final ShapeTransform shaper = shape.path.copy().reset();

    final Insets paintBorder = shape.path.transformInsets(getTabsBorder().getEffectiveBorder());

    int topY = shape.labelPath.getMaxY() + shape.labelPath.deltaY(1);

    int bottomY = topY + paintBorder.top - 2;
    int middleY = topY + (bottomY - topY) / 2;

    final int boundsX = shape.path.getX() + shape.path.deltaX(shape.insets.left);

    final int boundsY =
      isHideTabs() ? shape.path.getY() + shape.path.deltaY(shape.insets.top) : shape.labelPath.getMaxY() + shape.path.deltaY(1);

    final int boundsHeight = Math.abs(shape.path.getMaxY() - boundsY) - shape.insets.bottom - paintBorder.bottom;
    final int boundsWidth = Math.abs(shape.path.getMaxX() - (shape.insets.left + shape.insets.right));

    if (paintBorder.top > 0) {
      if (isHideTabs()) {
        if (isToDrawBorderIfTabsHidden()) {
          g2d.setColor(borderColor);
          g2d.fill(shaper.reset().doRect(boundsX, boundsY, boundsWidth, 1).getShape());
        }
      }
      else {
        Color tabFillColor = getActiveTabColor(null);
        if (tabFillColor == null) {
          tabFillColor = shape.path.transformY1(shape.to, shape.from);
        }

        g2d.setColor(borderColor);
        g2d.fill(shaper.reset().doRect(boundsX, topY + shape.path.deltaY(1), boundsWidth, paintBorder.top - 1).getShape());
        

        g2d.setColor(new Color(0, 0, 0, 50));
        if (paintBorder.top == 2) {
          final Line2D.Float line = shape.path.transformLine(boundsX, topY, boundsX + shape.path.deltaX(boundsWidth - 1), topY);

          g2d.drawLine((int)line.x1, (int)line.y1, (int)line.x2, (int)line.y2);
        }
        else if (paintBorder.top > 2) {
//todo kirillk
//start hack
          int deltaY = 0;
          if (getPosition() == JBTabsPosition.bottom || getPosition() == JBTabsPosition.right) {
            deltaY = 1;
          }
//end hack
          final int topLine = topY + shape.path.deltaY(paintBorder.top - 1);
          g2d.fill(shaper.reset().doRect(boundsX, topLine + deltaY, boundsWidth, 1).getShape());
        }
      }
    }

    g2d.setColor(borderColor);

    ////bottom
    //g2d.fill(shaper.reset().doRect(boundsX, Math.abs(shape.path.getMaxY() - shape.insets.bottom - paintBorder.bottom), boundsWidth,
    //                               paintBorder.bottom).getShape());
    //
    ////left
    //g2d.fill(shaper.reset().doRect(boundsX, boundsY, paintBorder.left, boundsHeight).getShape());
    //
    ////right
    //g2d.fill(shaper.reset()
    //  .doRect(shape.path.getMaxX() - shape.insets.right - paintBorder.right, boundsY, paintBorder.right, boundsHeight).getShape());
    //
  }
  
  protected Pair<ShapeInfo, ShapeTransform> computeSelectedLabelShape2() {
    final ShapeInfo shape = new ShapeInfo();

    TabInfo selected = getSelectedInfo();

    shape.path = getEffectiveLayout().createShapeTransform(getSize());
    shape.insets = shape.path.transformInsets(getLayoutInsets());
    shape.labelPath = shape.path.createTransform(getSelectedLabel().getBounds());
    
    ShapeTransform shadowShape = shape.path.createTransform(getSelectedLabel().getBounds());

    shape.labelBottomY = shape.labelPath.getMaxY() + shape.labelPath.deltaY(1);
    shape.labelTopY = shape.labelPath.getY() + getSelectionTabVShift();
    shape.labelLeftX = shape.labelPath.getX() - getArcSize();
    shape.labelRightX = shape.labelPath.getMaxX() - 1;

    boolean first = getLastLayoutPass().getPreviousFor(selected) == null;
    boolean last = getLastLayoutPass().getNextFor(selected) == null;
    
    if (first && !isGhostsAlwaysVisible()) {
      shape.path.moveTo(shape.labelLeftX + getArcSize(), shape.labelBottomY);
    }
    else {
      shape.path.moveTo(shape.insets.left, shape.labelBottomY);
      shape.path.lineTo(shape.labelLeftX, shape.labelBottomY);

      // left bottom quad
      shape.path.quadTo(shape.labelLeftX + shape.path.deltaX(getArcSize()), shape.labelBottomY,
                        shape.labelLeftX + getArcSize(), shape.labelBottomY - shape.path.deltaY(getArcSize()));
    }
    
    shape.path.lineTo(shape.labelLeftX + getArcSize(), shape.labelTopY + 1 + shape.labelPath.deltaY(getArcSize()));
    shape.path.quadTo(shape.labelLeftX + shape.path.deltaX(getArcSize()), shape.labelTopY + 1,
                      shape.labelLeftX + getArcSize() + shape.labelPath.deltaX(getArcSize()), shape.labelTopY + 1);

    shadowShape.moveTo(shape.labelLeftX + getArcSize() - 1, shape.labelBottomY - 2);
    shadowShape.lineTo(shape.labelLeftX + getArcSize() - 1, shape.labelTopY + shape.labelPath.deltaY(getArcSize()));
    shadowShape
      .quadTo(shape.labelLeftX - 1 + shape.path.deltaX(getArcSize()), shape.labelTopY, shape.labelLeftX + getArcSize() - 1 + shape.labelPath.deltaX(getArcSize()),
              shape.labelTopY);

    int lastX = shape.path.getWidth() - shape.path.deltaX(shape.insets.right);

    if (isStealthModeEffective()) {
      shape.path.lineTo(lastX - shape.path.deltaX(getArcSize()), shape.labelTopY);
      shape.path.quadTo(lastX, shape.labelTopY, lastX, shape.labelTopY + shape.path.deltaY(getArcSize()));
      shape.path.lineTo(lastX, shape.labelBottomY);
    }
    else {
      shape.path.lineTo(shape.labelRightX - shape.path.deltaX(getArcSize()), shape.labelTopY + 1);
      shape.path.quadTo(shape.labelRightX, shape.labelTopY + 1, shape.labelRightX, shape.labelTopY + 1 + shape.path.deltaY(getArcSize()));

      shadowShape.lineTo(shape.labelRightX - shape.path.deltaX(getArcSize()) + 1, shape.labelTopY);
      shadowShape.quadTo(shape.labelRightX + 1, shape.labelTopY, shape.labelRightX + 1, shape.labelTopY + shape.path.deltaY(getArcSize()));

      if (getLastLayoutPass().hasCurveSpaceFor(selected)) {
        shape.path.lineTo(shape.labelRightX, shape.labelBottomY - shape.path.deltaY(getArcSize()));
        shape.path.quadTo(shape.labelRightX, shape.labelBottomY, shape.labelRightX + shape.path.deltaX(getArcSize()), shape.labelBottomY);
        
        shadowShape.lineTo(shape.labelRightX + 1, shape.labelBottomY - shape.path.deltaY(getArcSize()) + 1);
      }
      else {
        shape.path.lineTo(shape.labelRightX, shape.labelBottomY);
      }
    }

    shape.path.lineTo(lastX, shape.labelBottomY);

    if (isStealthModeEffective()) {
      shape.path.closePath();
    }

    shape.fillPath = shape.path.copy();
    if (!isHideTabs()) {
      shape.fillPath.lineTo(lastX, shape.labelBottomY + shape.fillPath.deltaY(1));
      Insets insets = shape.fillPath.transformInsets(getTabsBorder().getEffectiveBorder());
      shape.fillPath.lineTo(lastX, shape.labelBottomY + insets.top - 1);
      shape.fillPath.lineTo(shape.insets.left, shape.labelBottomY + insets.top - 1);
      shape.fillPath.closePath();
    }
    
    return Pair.create(shape, shadowShape);
  }
}
