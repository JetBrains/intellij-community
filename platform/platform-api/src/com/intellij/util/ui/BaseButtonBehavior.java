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

package com.intellij.util.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public abstract class BaseButtonBehavior {
  public static final int MOUSE_PRESSED_RELEASED = -1;

  private final JComponent myComponent;

  private boolean myHovered;
  private boolean myFocused;
  private boolean myPressedByMouse;
  private boolean myPressedByKeyboard;

  private final TimedDeadzone myMouseDeadzone;

  private int myActionTrigger;

  /**
   * @param sig parameter is used to avoid clash with the deprecated constructor
   */
  protected BaseButtonBehavior(JComponent component, Void sig) {
    this(component, TimedDeadzone.DEFAULT, sig);
  }

  /**
   * @param sig parameter is used to avoid clash with the deprecated constructor
   */
  protected BaseButtonBehavior(JComponent component, TimedDeadzone.Length mouseDeadzoneTime, @SuppressWarnings("unused") Void sig) {
    myComponent = component;
    myMouseDeadzone = new TimedDeadzone(mouseDeadzoneTime);
  }

  public void setupListeners() {
    myComponent.addMouseListener(new MyMouseListener());
    myComponent.addMouseMotionListener(new MyMouseMotionListener());
    myComponent.addKeyListener(new PressedKeyListener());
    myComponent.addFocusListener(new ButtonFocusListener());
    setActionTrigger(MouseEvent.MOUSE_RELEASED);
  }

  /**
   * @deprecated Please use one of the non-deprecated constructors and call explicitelly {@link BaseButtonBehavior#setupListeners()}
   * to install the listeners on the components
   */
  @Deprecated
  public BaseButtonBehavior(JComponent component) {
    this(component, TimedDeadzone.DEFAULT);
  }

  /**
   * @deprecated Please use one of the non-deprecated constructors and call explicitelly {@link BaseButtonBehavior#setupListeners()}
   * to install the listeners on the components
   */
  @Deprecated
  public BaseButtonBehavior(JComponent component, TimedDeadzone.Length mouseDeadzoneTime) {
    myComponent = component;
    myMouseDeadzone = new TimedDeadzone(mouseDeadzoneTime);
    setupListeners();
  }

  public void setActionTrigger(int trigger) {
    assert trigger == MouseEvent.MOUSE_PRESSED || trigger == MouseEvent.MOUSE_RELEASED || trigger == MOUSE_PRESSED_RELEASED;
    myActionTrigger = trigger;
  }

  public final boolean isHovered() {
    return myHovered;
  }

  private void setHovered(boolean hovered) {
    myHovered = hovered;
    repaintComponent();
  }

  public final boolean isFocused() {
    return myFocused;
  }

  private void setFocused(boolean isFocused) {
    myFocused = isFocused;
    repaintComponent();
  }

  public final boolean isPressedByMouse() {
    return myPressedByMouse;
  }

  private void setPressedByMouse(boolean pressedByMouse) {
    myPressedByMouse = pressedByMouse;
    repaintComponent();
  }

  public final boolean isPressedByKeyboard() {
    return myPressedByKeyboard;
  }

  private void setPressedByKeyboard(boolean isPressedByKeyboard) {
    myPressedByKeyboard = isPressedByKeyboard;
    repaintComponent();
  }

  public void setMouseDeadzone(final TimedDeadzone.Length deadZone) {
    myMouseDeadzone.setLength(deadZone);
  }

  protected void repaintComponent() {
    repaint(myComponent);
  }

  protected void repaint(Component c) {
    c.repaint();
  }

  private class MyMouseListener extends MouseAdapter {

    private boolean myWasPressedOnFocusTransfer;

    @Override
    public void mouseEntered(MouseEvent e) {
      myMouseDeadzone.enter(e);

      setHovered(true);
      repaintComponent();
    }

    @Override
    public void mouseExited(MouseEvent e) {
      myMouseDeadzone.clear();

      setHovered(false);
      setPressedByMouse(false);
      repaintComponent();
    }

    @Override
    public void mousePressed(MouseEvent e) {
      Component owner = IdeFocusManager.getInstance(null).getFocusOwner();
      myWasPressedOnFocusTransfer = owner == null;

      if (isDeadZone()) return;

      if (myActionTrigger == MouseEvent.MOUSE_RELEASED &&
          UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED)) {
        setPressedByMouse(true);
      }

      if (passIfNeeded(e)) return;

      if (myActionTrigger == MouseEvent.MOUSE_PRESSED ||
          myActionTrigger == MOUSE_PRESSED_RELEASED) {
        execute(e);
      }
      else {
        repaintComponent();
      }
    }


    @Override
    public void mouseReleased(MouseEvent e) {
      try {
        if (isDeadZone()) return;

        if (UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED)) {
          setPressedByMouse(false);
        }

        if (passIfNeeded(e)) return;

        if (myActionTrigger == MouseEvent.MOUSE_RELEASED ||
            myActionTrigger == MOUSE_PRESSED_RELEASED) {
          execute(e);
        }
        else {
          repaintComponent();
        }
      }
      finally {
        myWasPressedOnFocusTransfer = false;
      }
    }

    private boolean execute(MouseEvent e) {
      Point point = e.getPoint();
      if (point.x < 0 || point.x > myComponent.getWidth()) return true;
      if (point.y < 0 || point.y > myComponent.getHeight()) return true;

      repaintComponent();

      BaseButtonBehavior.this.execute(e);
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myComponent.isShowing()) {
          setHovered(false);
          myMouseDeadzone.clear();
        }
      });

      return false;
    }

    private boolean passIfNeeded(final MouseEvent e) {
      boolean actionClick = myActionTrigger == MOUSE_PRESSED_RELEASED
                            ? UIUtil.isActionClick(e, MouseEvent.MOUSE_PRESSED) || UIUtil.isActionClick(e, MouseEvent.MOUSE_RELEASED)
                            : UIUtil.isActionClick(e, myActionTrigger);
      if (actionClick) return false;

      pass(e);
      return true;
    }

    private boolean isDeadZone() {
      boolean considerDeadZone = !myWasPressedOnFocusTransfer;
      return considerDeadZone && myMouseDeadzone.isWithin();
    }
  }

  private class MyMouseMotionListener extends MouseMotionAdapter {
    @Override
    public void mouseMoved(final MouseEvent e) {
      myMouseDeadzone.enter(e);
    }
  }

  private class PressedKeyListener extends KeyAdapter {
    @Override
    public void keyPressed(KeyEvent e) {
      if (e.getModifiers() == 0 && e.getKeyCode() == KeyEvent.VK_SPACE) {
        setPressedByKeyboard(true);
        repaintComponent();
      }
    }

    @Override
    public void keyReleased(KeyEvent e) {
      if (e.getModifiers() == 0 && e.getKeyCode() == KeyEvent.VK_SPACE) {
        e.consume();
        RelativePoint point = RelativePoint.getCenterOf(myComponent);
        execute(point.toMouseEvent());
        setPressedByKeyboard(false);
        repaintComponent();
        return;
      }
      super.keyReleased(e);
    }
  }

  private class ButtonFocusListener extends FocusAdapter {
    @Override
    public void focusGained(FocusEvent e) {
      setFocused(true);
      repaintComponent();
    }

    @Override
    public void focusLost(FocusEvent e) {
      setFocused(false);
      setPressedByKeyboard(false);
      repaintComponent();
    }
  }

  protected abstract void execute(final MouseEvent e);

  protected void pass(MouseEvent e) {

  }
}
