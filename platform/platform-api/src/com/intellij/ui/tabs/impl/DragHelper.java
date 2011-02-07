/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ui.InplaceButton;
import com.intellij.ui.MouseDragHelper;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.tabs.TabInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

class DragHelper extends MouseDragHelper {

  JBTabsImpl myTabs;
  TabInfo myDragSource;
  Rectangle myDragOriginalRec;

  Rectangle myDragRec;
  Dimension myHoldDelta;

  Measurer myHorizontal = new Measurer.Width();
  Measurer myVertical = new Measurer.Height();

  private TabInfo myDragOutSource;

  public DragHelper(JBTabsImpl tabs) {
    super(tabs, tabs);
    myTabs = tabs;
  }


  @Override
  protected boolean isDragOut(MouseEvent event, Point dragToScreenPoint, Point startScreenPoint) {
    if (myDragSource == null || !myDragSource.canBeDraggedOut()) return false;

    TabLabel label = myTabs.myInfo2Label.get(myDragSource);

    int dX = dragToScreenPoint.x - startScreenPoint.x;
    int dY = dragToScreenPoint.y - startScreenPoint.y;
    boolean dragOut =
      myTabs.getEffectiveLayout().isDragOut(label, dX, dY);

    return dragOut;
  }

  @Override
  protected void processDragOut(MouseEvent event, Point dragToScreenPoint, Point startScreenPoint, boolean justStarted) {
    TabInfo.DragOutDelegate delegate = myDragOutSource.getDragOutDelegate();
    if (justStarted) {
      delegate.dragOutStarted(event, myDragOutSource);
    } else {
      delegate.processDragOut(event, myDragOutSource);
    }
  }

  @Override
  protected void processDragOutFinish(MouseEvent event) {
    super.processDragOutFinish(event);

    myDragOutSource.getDragOutDelegate().dragOutFinished(event, myDragSource);
  }

  protected void processDrag(MouseEvent event, Point targetScreenPoint, Point startPointScreen) {
    if (!myTabs.isTabDraggingEnabled()) return;

    SwingUtilities.convertPointFromScreen(startPointScreen, myTabs);

    if (isDragJustStarted()) {
      final TabLabel label = findLabel(startPointScreen);
      if (label == null) return;

      final Rectangle labelBounds = label.getBounds();

      myHoldDelta = new Dimension(startPointScreen.x - labelBounds.x, startPointScreen.y - labelBounds.y);
      myDragSource = label.getInfo();
      myDragRec = new Rectangle(startPointScreen, labelBounds.getSize());
      myDragOriginalRec = (Rectangle)myDragRec.clone();

      myDragOriginalRec.x -= myHoldDelta.width;
      myDragOriginalRec.y -= myHoldDelta.height;
    }
    else {
      if (myDragRec == null) return;

      final Point toPoint = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), myTabs);

      myDragRec.x = toPoint.x;
      myDragRec.y = toPoint.y;
    }

    myDragRec.x -= myHoldDelta.width;
    myDragRec.y -= myHoldDelta.height;

    final Rectangle headerRec = myTabs.myLastLayoutPass.getHeaderRectangle();
    ScreenUtil.moveToFit(myDragRec, headerRec, null);

    int deadZoneX = 0;
    int deadZoneY = 0;

    final TabLabel top = findLabel(new Point(myDragRec.x + myDragRec.width / 2, myDragRec.y + deadZoneY));
    final TabLabel bottom = findLabel(new Point(myDragRec.x + myDragRec.width / 2, myDragRec.y + myDragRec.height - deadZoneY));
    final TabLabel left = findLabel(new Point(myDragRec.x + deadZoneX, myDragRec.y + myDragRec.height / 2));
    final TabLabel right = findLabel(new Point(myDragRec.x + myDragRec.width - deadZoneX, myDragRec.y + myDragRec.height / 2));


    TabLabel targetLabel;
    if (myTabs.isHorizontalTabs()) {
      targetLabel = findMostOverlapping(myHorizontal, left, right);
      if (targetLabel == null) {
        targetLabel = findMostOverlapping(myVertical, top, bottom);
      }
    } else {
      targetLabel = findMostOverlapping(myVertical, top, bottom);
      if (targetLabel == null) {
        targetLabel = findMostOverlapping(myHorizontal, left, right);
      }
    }

    if (targetLabel != null) {
      Rectangle saved = myDragRec;
      myDragRec = null;
      myTabs.reallocate(myDragSource, targetLabel.getInfo(), true);
      myDragOriginalRec = myTabs.myInfo2Label.get(myDragSource).getBounds();
      myDragRec = saved;
      myTabs.moveDraggedTabLabel();
    } else {
      myTabs.moveDraggedTabLabel();
      final int border = myTabs.getTabsBorder().getTabBorderSize();
      headerRec.x -= border;
      headerRec.y -= border;
      headerRec.width += border * 2;
      headerRec.height += border * 2;
      myTabs.repaint(headerRec);
    }
  }

  private TabLabel findMostOverlapping(Measurer measurer, TabLabel... labels) {
    double freeSpace;

    if (measurer.getMinValue(myDragRec) < measurer.getMinValue(myDragOriginalRec)) {
      freeSpace = measurer.getMaxValue(myDragOriginalRec) - measurer.getMaxValue(myDragRec);
    } else {
      freeSpace = measurer.getMinValue(myDragRec) - measurer.getMinValue(myDragOriginalRec);
    }


    int max = -1;
    TabLabel maxLabel = null;
    for (TabLabel each : labels) {
      if (each == null) continue;

      final Rectangle eachBounds = each.getBounds();
      if (measurer.getMeasuredValue(eachBounds) > freeSpace + freeSpace *0.3) continue;

      Rectangle intersection = myDragRec.intersection(eachBounds);
      int size = intersection.width * intersection.height;
      if (size > max) {
        max = size;
        maxLabel = each;
      }
    }

    return maxLabel;
  }

  interface Measurer {
    int getMinValue(Rectangle r);
    int getMaxValue(Rectangle r);
    int getMeasuredValue(Rectangle r);

    class Width implements Measurer{
      public int getMinValue(Rectangle r) {
        return r.x;
      }

      public int getMaxValue(Rectangle r) {
        return (int)r.getMaxX();
      }

      public int getMeasuredValue(Rectangle r) {
        return r.width;
      }
    }

    class Height implements Measurer{
      public int getMinValue(Rectangle r) {
        return r.y;
      }

      public int getMaxValue(Rectangle r) {
        return (int)r.getMaxY();
      }

      public int getMeasuredValue(Rectangle r) {
        return r.height;
      }
    }
  }


  @Nullable
  private TabLabel findLabel(Point dragPoint) {
    final Component at = myTabs.findComponentAt(dragPoint);
    if (at instanceof InplaceButton) return null;
    final TabLabel label = findLabel(at);

    return label != null && label.getParent() == myTabs && label.getInfo() != myDragSource ? label : null;

  }

  @Nullable
  private TabLabel findLabel(Component c) {
    Component eachParent = c;
    while (eachParent != null && eachParent != myTabs) {
      if (eachParent instanceof TabLabel) return (TabLabel)eachParent;
      eachParent = eachParent.getParent();
    }

    return null;
  }


  @Override
  protected boolean canStartDragging(JComponent dragComponent, Point dragComponentPoint) {
    return findLabel(dragComponentPoint) != null;
  }

  @Override
  protected void processDragFinish(MouseEvent event, boolean willDragOutStart) {
    super.processDragFinish(event, willDragOutStart);

    if (willDragOutStart) {
      myDragOutSource = myDragSource;
    }

    myDragSource = null;
    myDragRec = null;

    myTabs.resetTabsCache();
    myTabs.relayout(true, false);

    myTabs.revalidate();
  }

  public TabInfo getDragSource() {
    return myDragSource;
  }
}