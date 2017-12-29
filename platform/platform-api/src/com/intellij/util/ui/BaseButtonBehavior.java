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
import com.intellij.util.ui.accessibility.ScreenReader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public abstract class BaseButtonBehavior {

  private JComponent myComponent;

  private boolean myHovered;
  private boolean myPressedByMouse;

  private final TimedDeadzone myMouseDeadzone;

  private int myActionTrigger;

  public BaseButtonBehavior(JComponent component) {
    this(component, TimedDeadzone.DEFAULT);
  }

  public BaseButtonBehavior(JComponent component, TimedDeadzone.Length mouseDeadzoneTime) {
    myComponent = component;
    myMouseDeadzone = new TimedDeadzone(mouseDeadzoneTime);
    myComponent.addMouseListener(new MyMouseListener());
    myComponent.addMouseMotionListener(new MyMouseMotionListener());
    setActionTrigger(MouseEvent.MOUSE_RELEASED);
    if (ScreenReader.isActive()) {
      myComponent.addKeyListener(new KeyAdapter() {
        @Override
        public void keyReleased(KeyEvent e) {
          if (e.getModifiers() == 0 && e.getKeyCode() == KeyEvent.VK_SPACE) {
            e.consume();
            RelativePoint point = new RelativePoint(myComponent, new Point(myComponent.getWidth() / 2, myComponent.getHeight() / 2));
            execute(point.toMouseEvent());
            return;
          }
          super.keyReleased(e);
        }
      });
      myComponent.addFocusListener(new FocusListener() {
        @Override
        public void focusGained(FocusEvent e) {
          repaintComponent();
        }

        @Override
        public void focusLost(FocusEvent e) {
          repaintComponent();
        }
      });
    }
  }

  public void setActionTrigger(int trigger) {
    assert trigger == MouseEvent.MOUSE_PRESSED || trigger == MouseEvent.MOUSE_RELEASED;
    myActionTrigger = trigger;
  }

  public final boolean isHovered() {
    return myHovered;
  }

  private void setHovered(boolean hovered) {
    myHovered = hovered;
    repaintComponent();
  }

  public final boolean isPressedByMouse() {
    return myPressedByMouse;
  }

  private void setPressedByMouse(boolean pressedByMouse) {
    myPressedByMouse = pressedByMouse;
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

    public void mouseEntered(MouseEvent e) {
      myMouseDeadzone.enter(e);

      setHovered(true);
      repaintComponent();
    }

    public void mouseExited(MouseEvent e) {
      myMouseDeadzone.clear();

      setHovered(false);
      repaintComponent();
    }

    public void mousePressed(MouseEvent e) {
      Component owner = IdeFocusManager.getInstance(null).getFocusOwner();
      myWasPressedOnFocusTransfer = owner == null;

      if (passIfNeeded(e, !myWasPressedOnFocusTransfer)) return;

      setPressedByMouse(true);

      if (myActionTrigger == MouseEvent.MOUSE_PRESSED) {
        execute(e);
      } else {
        repaintComponent();
      }
    }


    public void mouseReleased(MouseEvent e) {
      try {
        if (passIfNeeded(e, !myWasPressedOnFocusTransfer)) return;

        setPressedByMouse(false);

        if (myActionTrigger == MouseEvent.MOUSE_RELEASED) {
          execute(e);
        } else {
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

    private boolean passIfNeeded(final MouseEvent e, boolean considerDeadZone) {
      final boolean actionClick = UIUtil.isActionClick(e, myActionTrigger);

      if (!actionClick || (considerDeadZone && myMouseDeadzone.isWithin())) {
        pass(e);
        return true;
      }
      return false;
    }

  }

  private class MyMouseMotionListener extends MouseMotionAdapter {
    @Override
    public void mouseMoved(final MouseEvent e) {
      myMouseDeadzone.enter(e);
    }
  }

  protected abstract void execute(final MouseEvent e);

  protected void pass(MouseEvent e) {

  }

}
