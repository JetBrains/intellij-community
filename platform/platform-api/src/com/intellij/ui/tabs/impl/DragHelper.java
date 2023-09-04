// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs.impl;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.UISettings;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.reference.SoftReference;
import com.intellij.ui.InplaceButton;
import com.intellij.ui.MouseDragHelper;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.ui.util.Axis;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

public class DragHelper extends MouseDragHelper<JBTabsImpl> {
  private final JBTabsImpl myTabs;
  private TabInfo myDragSource;
  private Rectangle myDragOriginalRec;

  Rectangle dragRec;
  private Dimension myHoldDelta;

  private TabInfo myDragOutSource;
  private Reference<TabLabel> myPressedTabLabel;

  protected DragHelper(@NotNull JBTabsImpl tabs, @NotNull Disposable parentDisposable) {
    super(parentDisposable, tabs);

    myTabs = tabs;
  }

  @Override
  protected boolean isDragOut(@NotNull MouseEvent event, @NotNull Point dragToScreenPoint, @NotNull Point startScreenPoint) {
    if (myDragSource == null || !myDragSource.canBeDraggedOut()) {
      return false;
    }

    TabLabel label = myTabs.getInfoToLabel().get(myDragSource);
    if (label == null) {
      return false;
    }

    int dX = dragToScreenPoint.x - startScreenPoint.x;
    int dY = dragToScreenPoint.y - startScreenPoint.y;

    return myTabs.isDragOut(label, dX, dY);
  }

  @Override
  protected void processDragOut(@NotNull MouseEvent event, @NotNull Point dragToScreenPoint, @NotNull Point startScreenPoint, boolean justStarted) {
    if (!MouseDragHelper.checkModifiers(event)) {
      if (myDragOutSource != null) {
        processDragOutCancel();
      }
      return;
    }
    TabInfo.DragOutDelegate delegate = myDragOutSource.getDragOutDelegate();
    if (justStarted) {
      delegate.dragOutStarted(event, myDragOutSource);
    }
    delegate.processDragOut(event, myDragOutSource);
    event.consume();
  }

  @Override
  protected void processDragOutFinish(@NotNull MouseEvent event) {
    if (!MouseDragHelper.checkModifiers(event)) {
      if (myDragOutSource != null) {
        processDragOutCancel();
      }
      return;
    }
    super.processDragOutFinish(event);
    boolean wasSorted = prepareDisableSorting();
    try {
      myDragOutSource.getDragOutDelegate().dragOutFinished(event, myDragOutSource);
    }
    finally {
      disableSortingIfNeed(event, wasSorted);
      myDragOutSource = null;
    }
  }

  private static boolean prepareDisableSorting() {
    boolean wasSorted = UISettings.getInstance().getSortTabsAlphabetically() /*&& myTabs.isAlphabeticalMode()*/;
    if (wasSorted && !UISettings.getInstance().getAlwaysKeepTabsAlphabeticallySorted()) {
      UISettings.getInstance().setSortTabsAlphabetically(false);
    }
    return wasSorted;
  }

  private static void disableSortingIfNeed(@NotNull MouseEvent event, boolean wasSorted) {
    if (!wasSorted) return;

    if (event.isConsumed() || UISettings.getInstance().getAlwaysKeepTabsAlphabeticallySorted()) {//new container for separate window was created, see DockManagerImpl.MyDragSession
      UISettings.getInstance().setSortTabsAlphabetically(true);
    }
    else {
      UISettings.getInstance().fireUISettingsChanged();
      ApplicationManager.getApplication().invokeLater(() -> {
        Notification notification =
          new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, IdeBundle.message("alphabetical.mode.is.on.notification"), "", NotificationType.INFORMATION);
        notification.addAction(
          DumbAwareAction.create(IdeBundle.message("editor.tabs.enable.sorting"), e -> {
            UISettings.getInstance().setSortTabsAlphabetically(true);
            UISettings.getInstance().fireUISettingsChanged();
            notification.expire();
          }))
          .addAction(
            DumbAwareAction.create(IdeBundle.message("editor.tabs.always.keep.sorting"), e -> {
              UISettings.getInstance().setAlwaysKeepTabsAlphabeticallySorted(true);
              UISettings.getInstance().setSortTabsAlphabetically(true);
              UISettings.getInstance().fireUISettingsChanged();
              notification.expire();
            }));
        Notifications.Bus.notify(notification);
      });
    }
  }

  @Override
  protected void processDragOutCancel() {
    myDragOutSource.getDragOutDelegate().dragOutCancelled(myDragOutSource);
    myDragOutSource = null;
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
      dragRec = new Rectangle(startPointScreen, labelBounds.getSize());
      myDragOriginalRec = (Rectangle)dragRec.clone();

      myDragOriginalRec.x -= myHoldDelta.width;
      myDragOriginalRec.y -= myHoldDelta.height;

      TabInfo.DragDelegate delegate = myDragSource.getDragDelegate();
      if (delegate != null) {
        delegate.dragStarted(event);
      }
    }
    else {
      if (dragRec == null) return;

      final Point toPoint = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), myTabs);

      dragRec.x = toPoint.x;
      dragRec.y = toPoint.y;
    }

    dragRec.x -= myHoldDelta.width;
    dragRec.y -= myHoldDelta.height;

    final Rectangle headerRec = myTabs.getLastLayoutPass().getHeaderRectangle();
    ScreenUtil.moveToFit(dragRec, headerRec, null);

    int deadZoneX = 0;
    int deadZoneY = 0;

    final TabLabel top = findLabel(new Point(dragRec.x + dragRec.width / 2, dragRec.y + deadZoneY));
    final TabLabel bottom = findLabel(new Point(dragRec.x + dragRec.width / 2, dragRec.y + dragRec.height - deadZoneY));
    final TabLabel left = findLabel(new Point(dragRec.x + deadZoneX, dragRec.y + dragRec.height / 2));
    final TabLabel right = findLabel(new Point(dragRec.x + dragRec.width - deadZoneX, dragRec.y + dragRec.height / 2));


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
      Rectangle saved = dragRec;
      dragRec = null;
      myTabs.reallocate(myDragSource, targetLabel.getInfo());
      myDragOriginalRec = myTabs.getInfoToLabel().get(myDragSource).getBounds();
      dragRec = saved;
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

    if (measurer.getMinValue(dragRec) < measurer.getMinValue(myDragOriginalRec)) {
      freeSpace = measurer.getMaxValue(myDragOriginalRec) - measurer.getMaxValue(dragRec);
    }
    else {
      freeSpace = measurer.getMinValue(dragRec) - measurer.getMinValue(myDragOriginalRec);
    }


    int max = -1;
    TabLabel maxLabel = null;
    for (TabLabel each : labels) {
      if (each == null) continue;

      final Rectangle eachBounds = each.getBounds();
      if (measurer.getSize(eachBounds) > freeSpace + freeSpace *0.3) continue;

      Rectangle intersection = dragRec.intersection(eachBounds);
      int size = intersection.width * intersection.height;
      if (size > max) {
        max = size;
        maxLabel = each;
      }
    }

    return maxLabel;
  }


  private @Nullable TabLabel findLabel(Point dragPoint) {
    final Component at = myTabs.findComponentAt(dragPoint);
    if (at instanceof InplaceButton) return null;
    final TabLabel label = findLabel(at);

    return label != null && label.getParent() == myTabs && label.getInfo() != myDragSource ? label : null;

  }

  private @Nullable TabLabel findLabel(Component c) {
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
  protected boolean canFinishDragging(@NotNull JComponent component, @NotNull RelativePoint point) {
    Component realDropTarget = UIUtil.getDeepestComponentAt(point.getOriginalComponent(), point.getOriginalPoint().x, point.getOriginalPoint().y);
    if (realDropTarget == null) realDropTarget = SwingUtilities.getDeepestComponentAt(point.getOriginalComponent(), point.getOriginalPoint().x, point.getOriginalPoint().y);
    if (myTabs.getVisibleInfos().isEmpty() && realDropTarget != null ) {
      JBTabsImpl tabs = UIUtil.getParentOfType(JBTabsImpl.class, realDropTarget);
      if (tabs == null || !tabs.isEditorTabs()) return false;
    }
    return !myTabs.contains(point.getPoint(myTabs)) || !myTabs.getVisibleInfos().isEmpty();
  }

  @Override
  protected void processDragFinish(@NotNull MouseEvent event, boolean willDragOutStart) {
    boolean checkModifiers = MouseDragHelper.checkModifiers(event);
    if (!checkModifiers && myDragSource == null) {
      return;
    }
    super.processDragFinish(event, willDragOutStart);
    boolean wasSorted = !willDragOutStart && prepareDisableSorting();
    try {
      endDrag(willDragOutStart && checkModifiers);
    } finally {
      disableSortingIfNeed(event, wasSorted);
    }
  }

  private void endDrag(boolean willDragOutStart) {
    if (willDragOutStart) {
      myDragOutSource = myDragSource;
    }

    myTabs.resetTabsCache();
    if (!willDragOutStart) {
     myTabs.fireTabsMoved();
    }
    myTabs.relayout(true, false);

    myTabs.revalidate();

    TabInfo.DragDelegate delegate = myDragSource.getDragDelegate();
    if (delegate != null) {
      delegate.dragFinishedOrCanceled();
    }

    myDragSource = null;
    dragRec = null;
  }

  @Override
  protected void processDragCancel() {
    endDrag(false);
  }

  public TabInfo getDragSource() {
    return myDragSource;
  }
}
