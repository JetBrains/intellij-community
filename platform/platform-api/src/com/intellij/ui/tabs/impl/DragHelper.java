// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Messages;
import com.intellij.reference.SoftReference;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.MouseDragHelper;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.tabs.JBTabsPosition;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.util.Axis;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

final class DragHelper extends MouseDragHelper {
  private final JBTabsImpl myTabs;
  private TabInfo myDragSource;
  private Rectangle myDragOriginalRec;

  Rectangle myDragRec;
  private Dimension myHoldDelta;

  private TabInfo myDragOutSource;
  private Reference<TabLabel> myPressedTabLabel;

  DragHelper(@NotNull JBTabsImpl tabs, @NotNull Disposable parentDisposable) {
    super(parentDisposable, tabs);

    myTabs = tabs;
  }

  @Override
  protected boolean isDragOut(@NotNull MouseEvent event, @NotNull Point dragToScreenPoint, @NotNull Point startScreenPoint) {
    if (myDragSource == null || !myDragSource.canBeDraggedOut()) {
      return false;
    }

    TabLabel label = myTabs.myInfo2Label.get(myDragSource);
    if (label == null) {
      return false;
    }

    int dX = dragToScreenPoint.x - startScreenPoint.x;
    int dY = dragToScreenPoint.y - startScreenPoint.y;

    return myTabs.isDragOut(label, dX, dY);
  }

  @Override
  protected void processDragOut(@NotNull MouseEvent event, @NotNull Point dragToScreenPoint, @NotNull Point startScreenPoint, boolean justStarted) {
    TabInfo.DragOutDelegate delegate = myDragOutSource.getDragOutDelegate();
    if (justStarted) {
      delegate.dragOutStarted(event, myDragOutSource);
    }
    delegate.processDragOut(event, myDragOutSource);
    event.consume();
  }

  @Override
  protected void processDragOutFinish(@NotNull MouseEvent event) {
    super.processDragOutFinish(event);

    myDragOutSource.getDragOutDelegate().dragOutFinished(event, myDragOutSource);
  }

  @Override
  protected void processDragOutCancel() {
    myDragOutSource.getDragOutDelegate().dragOutCancelled(myDragOutSource);
  }

  @Override
  protected void processMousePressed(@NotNull MouseEvent event) {
    // since selection change can cause tabs to be reordered, we need to remember the tab on which the mouse was pressed, otherwise
    // we'll end up dragging the wrong tab (IDEA-65073)
    TabLabel label = findLabel(new RelativePoint(event).getPoint(myTabs));
    myPressedTabLabel = label == null ? null : new WeakReference<>(label);
  }

  @Override
  protected void processDrag(@NotNull MouseEvent event, @NotNull Point targetScreenPoint, @NotNull Point startPointScreen) {
    if (!myTabs.isTabDraggingEnabled() || !isDragSource(event) || !MouseDragHelper.checkModifiers(event)) return;

    SwingUtilities.convertPointFromScreen(startPointScreen, myTabs);

    if (isDragJustStarted()) {
      TabLabel pressedTabLabel = SoftReference.dereference(myPressedTabLabel);
      if (pressedTabLabel == null) return;

      final Rectangle labelBounds = pressedTabLabel.getBounds();

      myHoldDelta = new Dimension(startPointScreen.x - labelBounds.x, startPointScreen.y - labelBounds.y);
      myDragSource = pressedTabLabel.getInfo();
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

    final Rectangle headerRec = myTabs.getLastLayoutPass().getHeaderRectangle();
    ScreenUtil.moveToFit(myDragRec, headerRec, null);

    int deadZoneX = 0;
    int deadZoneY = 0;

    final TabLabel top = findLabel(new Point(myDragRec.x + myDragRec.width / 2, myDragRec.y + deadZoneY));
    final TabLabel bottom = findLabel(new Point(myDragRec.x + myDragRec.width / 2, myDragRec.y + myDragRec.height - deadZoneY));
    final TabLabel left = findLabel(new Point(myDragRec.x + deadZoneX, myDragRec.y + myDragRec.height / 2));
    final TabLabel right = findLabel(new Point(myDragRec.x + myDragRec.width - deadZoneX, myDragRec.y + myDragRec.height / 2));


    TabLabel targetLabel;
    if (myTabs.isHorizontalTabs()) {
      targetLabel = findMostOverlapping(Axis.X, left, right);
      if (targetLabel == null) {
        targetLabel = findMostOverlapping(Axis.Y, top, bottom);
      }
    } else {
      targetLabel = findMostOverlapping(Axis.Y, top, bottom);
      if (targetLabel == null) {
        targetLabel = findMostOverlapping(Axis.X, left, right);
      }
    }

    if (targetLabel != null) {
      Rectangle saved = myDragRec;
      myDragRec = null;
      myTabs.reallocate(myDragSource, targetLabel.getInfo());
      myDragOriginalRec = myTabs.myInfo2Label.get(myDragSource).getBounds();
      myDragRec = saved;
      myTabs.moveDraggedTabLabel();
    } else {
      myTabs.moveDraggedTabLabel();
      final int border = myTabs.getBorderThickness();
      headerRec.x -= border;
      headerRec.y -= border;
      headerRec.width += border * 2;
      headerRec.height += border * 2;
      myTabs.repaint(headerRec);
    }
    event.consume();
  }

  private boolean isDragSource(MouseEvent event) {
    final Object source = event.getSource();
    if (source instanceof Component) {
      return SwingUtilities.windowForComponent(myTabs) == SwingUtilities.windowForComponent((Component)source);
    }
    return false;
  }

  private TabLabel findMostOverlapping(Axis measurer, TabLabel... labels) {
    double freeSpace;

    if (measurer.getMinValue(myDragRec) < measurer.getMinValue(myDragOriginalRec)) {
      freeSpace = measurer.getMaxValue(myDragOriginalRec) - measurer.getMaxValue(myDragRec);
    }
    else {
      freeSpace = measurer.getMinValue(myDragRec) - measurer.getMinValue(myDragOriginalRec);
    }


    int max = -1;
    TabLabel maxLabel = null;
    for (TabLabel each : labels) {
      if (each == null) continue;

      final Rectangle eachBounds = each.getBounds();
      if (measurer.getSize(eachBounds) > freeSpace + freeSpace *0.3) continue;

      Rectangle intersection = myDragRec.intersection(eachBounds);
      int size = intersection.width * intersection.height;
      if (size > max) {
        max = size;
        maxLabel = each;
      }
    }

    return maxLabel;
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
  protected boolean canStartDragging(@NotNull JComponent dragComponent, @NotNull Point dragComponentPoint) {
    return findLabel(dragComponentPoint) != null;
  }

  @Override
  protected void processDragFinish(@NotNull MouseEvent event, boolean willDragOutStart) {
    super.processDragFinish(event, willDragOutStart);

    endDrag(willDragOutStart);

    final JBTabsPosition position = myTabs.getTabsPosition();

    if (!willDragOutStart && myTabs.isAlphabeticalMode() && position != JBTabsPosition.top && position != JBTabsPosition.bottom) {
      Point p = new Point(event.getPoint());
      p = SwingUtilities.convertPoint(event.getComponent(), p, myTabs);
      if (myTabs.getVisibleRect().contains(p) && myPressedOnScreenPoint.distance(new RelativePoint(event).getScreenPoint()) > 15) {
        final int answer = Messages.showOkCancelDialog(myTabs,
                                                       IdeBundle.message("alphabetical.mode.is.on.warning"),
                                                       IdeBundle.message("title.warning"),
                                                       Messages.getQuestionIcon());
        if (answer == Messages.OK) {
          UISettings.getInstance().setSortTabsAlphabetically(false);
          myTabs.relayout(true, false);
          myTabs.revalidate();
        }
      }
    }
  }

  private void endDrag(boolean willDragOutStart) {
    if (willDragOutStart) {
      myDragOutSource = myDragSource;
    }

    myDragSource = null;
    myDragRec = null;

    myTabs.resetTabsCache();
    if (!willDragOutStart) {
     myTabs.fireTabsMoved();
    }
    myTabs.relayout(true, false);

    myTabs.revalidate();
  }

  @Override
  protected void processDragCancel() {
    endDrag(false);
  }

  public TabInfo getDragSource() {
    return myDragSource;
  }
}
