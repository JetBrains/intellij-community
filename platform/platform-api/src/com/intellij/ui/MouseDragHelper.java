// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.NullableComponent;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Weighted;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.openapi.wm.IdeGlassPaneUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public abstract class MouseDragHelper extends MouseAdapter implements MouseMotionListener, KeyEventDispatcher, Weighted {
  public static final int DRAG_START_DEADZONE = 7;

  @NotNull
  private final JComponent myDragComponent;

  private Point myPressPointScreen;
  protected Point myPressedOnScreenPoint;

  private boolean myDraggingNow;
  private boolean myDragJustStarted;
  private IdeGlassPane myGlassPane;
  @NotNull
  private final Disposable myParentDisposable;
  private Dimension myDelta;

  private boolean myDetachPostponed;
  private boolean myDetachingMode;
  private boolean myCancelled;
  private Disposable myGlassPaneListenersDisposable = Disposer.newDisposable();
  private boolean myStopped;

  public MouseDragHelper(@NotNull Disposable parent, @NotNull JComponent dragComponent) {
    myDragComponent = dragComponent;
    myParentDisposable = parent;
  }

  /**
   *
   * @return false if Settings -> Appearance -> Drag-n-Drop with ALT pressed only is selected but event doesn't have ALT modifier
   */
  public static boolean checkModifiers(@Nullable InputEvent event) {
    if (event == null || !UISettings.getInstance().getDndWithPressedAltOnly()) {
      return true;
    }
    return (event.getModifiers() & InputEvent.ALT_MASK) != 0;
  }

  public void start() {
    if (myGlassPane != null) {
      return;
    }

    new UiNotifyConnector(myDragComponent, new Activatable() {
      @Override
      public void showNotify() {
        attach();
      }

      @Override
      public void hideNotify() {
        detach(true);
      }
    });

    Disposer.register(myParentDisposable, () -> stop());
  }

  private void attach() {
    if (myDetachPostponed) {
      myDetachPostponed = false;
      return;
    }

    if (myStopped || myGlassPane != null) {
      return;
    }

    myGlassPane = IdeGlassPaneUtil.find(myDragComponent);
    myGlassPaneListenersDisposable = Disposer.newDisposable("myGlassPaneListeners");
    Disposer.register(myParentDisposable, myGlassPaneListenersDisposable);
    myGlassPane.addMousePreprocessor(this, myGlassPaneListenersDisposable);
    myGlassPane.addMouseMotionPreprocessor(this, myGlassPaneListenersDisposable);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
  }

  public void stop() {
    myStopped = true;
    detach(false);
  }

  private void detach(boolean canPostponeDetach) {
    if (canPostponeDetach && myDraggingNow) {
      myDetachPostponed = true;
      return;
    }
    if (myGlassPane != null) {
      Disposer.dispose(myGlassPaneListenersDisposable);
      myGlassPaneListenersDisposable = Disposer.newDisposable();
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
      myGlassPane = null;
    }
  }

  @Override
  public double getWeight() {
    return 2;
  }

  @Override
  public void mousePressed(final MouseEvent e) {
    if (!canStartDragging(e)) return;

    myPressPointScreen = new RelativePoint(e).getScreenPoint();
    myPressedOnScreenPoint = new Point(myPressPointScreen);
    processMousePressed(e);

    myDelta = new Dimension();
    if (myDragComponent.isShowing()) {
      final Point delta = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myDragComponent);
      myDelta.width = delta.x;
      myDelta.height = delta.y;
    }
  }

  @Override
  public void mouseReleased(final MouseEvent e) {
    if (myCancelled) {
      myCancelled = false;
      return;
    }
    if (myDraggingNow && !canFinishDragging(e)) {
      cancelDragging();
      e.consume();
      myPressedOnScreenPoint = null;
      myCancelled = false;
      return;
    }
    boolean wasDragging = myDraggingNow;
    myPressPointScreen = null;
    myDraggingNow = false;
    myDragJustStarted = false;

    if (wasDragging) {
      try {
        if (myDetachingMode) {
          processDragOutFinish(e);
        }
        else {
          processDragFinish(e, false);
        }
      }
      finally {
        myPressedOnScreenPoint = null;
        resetDragState();
        e.consume();
        if (myDetachPostponed) {
          myDetachPostponed = false;
          detach(false);
        }
      }
    }
  }

  private void resetDragState() {
    myDraggingNow = false;
    myDragJustStarted = false;
    myPressPointScreen = null;
    myDetachingMode = false;
  }

  @Override
  public void mouseDragged(@NotNull MouseEvent e) {
    if (myPressPointScreen == null || myCancelled) return;

    final boolean deadZone = isWithinDeadZone(e);
    if (!myDraggingNow && !deadZone) {
      myDraggingNow = true;
      myDragJustStarted = true;
    }
    else if (myDraggingNow) {
      myDragJustStarted = false;
    }

    if (myDraggingNow && myPressPointScreen != null) {
      final Point draggedTo = new RelativePoint(e).getScreenPoint();

      boolean dragOutStarted = false;
      if (!myDetachingMode) {
        if (isDragOut(e, draggedTo, (Point)myPressPointScreen.clone())) {
          myDetachingMode = true;
          processDragFinish(e, true);
          dragOutStarted = true;
        }
      }

      if (myDetachingMode) {
        processDragOut(e, draggedTo, (Point)myPressPointScreen.clone(), dragOutStarted);
      }
      else {
        processDrag(e, draggedTo, (Point)myPressPointScreen.clone());
      }
    }
  }

  private boolean canStartDragging(@NotNull MouseEvent me) {
    if (me.getButton() != MouseEvent.BUTTON1) return false;
    if (!myDragComponent.isShowing()) return false;

    Component component = me.getComponent();
    if (NullableComponent.Check.isNullOrHidden(component)) return false;
    final Point dragComponentPoint = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), myDragComponent);
    return canStartDragging(myDragComponent, dragComponentPoint);
  }

  protected boolean canStartDragging(@NotNull JComponent dragComponent, @NotNull Point dragComponentPoint) {
    return true;
  }

  protected boolean canFinishDragging(@NotNull MouseEvent me) {
    if (!myDragComponent.isShowing()) return false;
    Component component = me.getComponent();
    if (NullableComponent.Check.isNullOrHidden(component)) return false;
    return canFinishDragging(myDragComponent, new RelativePoint(me));
  }

  protected boolean canFinishDragging(@NotNull JComponent component, @NotNull RelativePoint point) {
    return true;
  }

  protected void processMousePressed(@NotNull MouseEvent event) {
  }

  protected void processDragCancel() {
  }

  protected void processDragFinish(@NotNull MouseEvent event, boolean willDragOutStart) {
  }

  protected void processDragOutFinish(@NotNull MouseEvent event) {
  }

  protected void processDragOutCancel() {
  }

  protected final boolean isDragJustStarted() {
    return myDragJustStarted;
  }

  protected abstract void processDrag(@NotNull MouseEvent event, @NotNull Point dragToScreenPoint, @NotNull Point startScreenPoint);

  protected boolean isDragOut(@NotNull MouseEvent event, @NotNull Point dragToScreenPoint, @NotNull Point startScreenPoint) {
    return false;
  }

  protected void processDragOut(@NotNull MouseEvent event, @NotNull Point dragToScreenPoint, @NotNull Point startScreenPoint, boolean justStarted) {
    event.consume();
  }

  private boolean isWithinDeadZone(@NotNull MouseEvent e) {
    final Point screen = new RelativePoint(e).getScreenPoint();
    return Math.abs(myPressPointScreen.x - screen.x - myDelta.width) < DRAG_START_DEADZONE &&
           Math.abs(myPressPointScreen.y - screen.y - myDelta.height) < DRAG_START_DEADZONE;
  }

  @Override
  public void mouseMoved(@NotNull final MouseEvent e) {
  }

  @Override
  public boolean dispatchKeyEvent(@NotNull KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getID() == KeyEvent.KEY_PRESSED) {
      return cancelDragging();
    }
    return false;
  }

  public boolean cancelDragging() {
    if (!myDraggingNow) return false;
    myCancelled = true;
    if (myDetachingMode) {
      processDragOutCancel();
    }
    else {
      processDragCancel();
    }
    resetDragState();
    return true;
  }
}
