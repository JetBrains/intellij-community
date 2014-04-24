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

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public abstract class MouseDragHelper implements MouseListener, MouseMotionListener, KeyEventDispatcher, Weighted {

  public static final int DRAG_START_DEADZONE = 7;

  private final JComponent myDragComponent;

  private Point myPressPointScreen;
  protected Point myPressedOnScreenPoint;
  private Point myPressPointComponent;

  private boolean myDraggingNow;
  private boolean myDragJustStarted;
  private IdeGlassPane myGlassPane;
  private final Disposable myParentDisposable;
  private Dimension myDelta;

  private boolean myDetachPostponed;
  private boolean myDetachingMode;
  private boolean myCancelled;

  public MouseDragHelper(Disposable parent, final JComponent dragComponent) {
    myDragComponent = dragComponent;
    myParentDisposable = parent;

  }

  /**
   *
   * @param event
   * @return false if Settings -> Appearance -> Drag-n-Drop with ALT pressed only is selected but event doesn't have ALT modifier
   */
  public static boolean checkModifiers(InputEvent event) {
    if (event == null || !UISettings.getInstance().DND_WITH_PRESSED_ALT_ONLY) return true;
    return (event.getModifiers() & InputEvent.ALT_MASK) != 0;
  }

  public void start() {
    if (myGlassPane != null) return;

    new UiNotifyConnector(myDragComponent, new Activatable() {
      public void showNotify() {
        attach();
      }

      public void hideNotify() {
        detach(true);
      }
    });

    Disposer.register(myParentDisposable, new Disposable() {
      public void dispose() {
        stop();
      }
    });
  }

  private void attach() {
    if (myDetachPostponed) {
      myDetachPostponed = false;
      return;
    }
    myGlassPane = IdeGlassPaneUtil.find(myDragComponent);
    myGlassPane.addMousePreprocessor(this, myParentDisposable);
    myGlassPane.addMouseMotionPreprocessor(this, myParentDisposable);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    Disposer.register(myParentDisposable, new Disposable() {
      @Override
      public void dispose() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(MouseDragHelper.this);
      }
    });
  }

  public void stop() {
    detach(false);
  }

  private void detach(boolean canPostponeDetach) {
    if (canPostponeDetach && myDraggingNow) {
      myDetachPostponed = true;
      return;
    }
    if (myGlassPane != null) {
      myGlassPane.removeMousePreprocessor(this);
      myGlassPane.removeMouseMotionPreprocessor(this);
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
      myGlassPane = null;
    }
  }

  @Override
  public double getWeight() {
    return 2;
  }

  public void mousePressed(final MouseEvent e) {
    if (!canStartDragging(e)) return;

    myPressPointScreen = new RelativePoint(e).getScreenPoint();
    myPressedOnScreenPoint = new Point(myPressPointScreen);
    myPressPointComponent = e.getPoint();
    processMousePressed(e);

    myDelta = new Dimension();
    if (myDragComponent.isShowing()) {
      final Point delta = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), myDragComponent);
      myDelta.width = delta.x;
      myDelta.height = delta.y;
    }
  }

  public void mouseReleased(final MouseEvent e) {
    if (myCancelled) {
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
        } else {
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
    myPressPointComponent = null;
    myPressPointScreen = null;
    myDetachingMode = false;
  }

  public void mouseDragged(final MouseEvent e) {
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
      } else {
        processDrag(e, draggedTo, (Point)myPressPointScreen.clone());
      }
    }
  }

  private boolean canStartDragging(MouseEvent me) {
    if (me.getButton() != MouseEvent.BUTTON1) return false;
    if (!myDragComponent.isShowing()) return false;

    Component component = me.getComponent();
    if (NullableComponent.Check.isNullOrHidden(component)) return false;
    final Point dragComponentPoint = SwingUtilities.convertPoint(me.getComponent(), me.getPoint(), myDragComponent);
    return canStartDragging(myDragComponent, dragComponentPoint);
  }

  protected boolean canStartDragging(final JComponent dragComponent, Point dragComponentPoint) {
    return true;
  }

  protected void processMousePressed(MouseEvent event) {
  }

  protected void processDragCancel() {
  }

  protected void processDragFinish(final MouseEvent event, boolean willDragOutStart) {
  }

  protected void processDragOutFinish(final MouseEvent event) {
  }

  protected void processDragOutCancel() {
  }

  public final boolean isDragJustStarted() {
    return myDragJustStarted;
  }

  protected abstract void processDrag(MouseEvent event, Point dragToScreenPoint, Point startScreenPoint);

  protected boolean isDragOut(MouseEvent event, Point dragToScreenPoint, Point startScreenPoint) {
    return false;
  }

  protected void processDragOut(MouseEvent event, Point dragToScreenPoint, Point startScreenPoint, boolean justStarted) {
    event.consume();
  }

  private boolean isWithinDeadZone(final MouseEvent e) {
    final Point screen = new RelativePoint(e).getScreenPoint();
    return Math.abs(myPressPointScreen.x - screen.x - myDelta.width) < DRAG_START_DEADZONE &&
           Math.abs(myPressPointScreen.y - screen.y - myDelta.height) < DRAG_START_DEADZONE;
  }

  public void mouseClicked(final MouseEvent e) {
  }

  public void mouseEntered(final MouseEvent e) {
  }

  public void mouseExited(final MouseEvent e) {
  }

  public void mouseMoved(final MouseEvent e) {
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getID() == KeyEvent.KEY_PRESSED && myDraggingNow) {
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
    return false;
  }
}
