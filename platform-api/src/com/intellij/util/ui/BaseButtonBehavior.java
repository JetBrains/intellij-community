/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import com.intellij.util.Alarm;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.*;

public abstract class BaseButtonBehavior {

  private JComponent myComponent;

  private boolean myHovered;
  private boolean myPressedByMouse;
  private boolean mySelected;


  private TimedDeadzone myMouseDeadzone;

  public BaseButtonBehavior(JComponent component) {
    this(component, TimedDeadzone.DEFAULT);
  }

  public BaseButtonBehavior(JComponent component, TimedDeadzone.Length mouseDeadzoneTime) {
    myComponent = component;
    myMouseDeadzone = new TimedDeadzone(mouseDeadzoneTime, Alarm.ThreadToUse.SWING_THREAD);
    myComponent.addMouseListener(new MyMouseListener());
    myComponent.addMouseMotionListener(new MyMouseMotionListener());
  }

  public final boolean isHovered() {
    return myHovered;
  }

  private void setHovered(boolean hovered) {
    myHovered = hovered;
    myComponent.repaint();
  }

  public final boolean isPressedByMouse() {
    return myPressedByMouse;
  }

  private void setPressedByMouse(boolean pressedByMouse) {
    myPressedByMouse = pressedByMouse;
    myComponent.repaint();
  }

  public final boolean isSelected() {
    return mySelected;
  }

  private void setSelected(boolean selected) {
    mySelected = selected;
  }

  private boolean isPressed() {
    return isSelected() || isPressedByMouse();
  }

  public void setMouseDeadzone(final TimedDeadzone.Length deadZone) {
    myMouseDeadzone.setLength(deadZone);
  }

  private class MyMouseListener extends MouseAdapter {
    public void mouseEntered(MouseEvent e) {
      myMouseDeadzone.reEnter();

      setHovered(true);
      myComponent.repaint();
    }

    public void mouseExited(MouseEvent e) {
      myMouseDeadzone.clear();

      setHovered(false);
      myComponent.repaint();
    }

    public void mousePressed(MouseEvent e) {
      if (passIfNeeded(e)) return;

      setPressedByMouse(true);
      myComponent.repaint();
    }


    public void mouseReleased(MouseEvent e) {
      if (passIfNeeded(e)) return;

      setPressedByMouse(false);

      Point point = e.getPoint();
      if (point.x < 0 || point.x > myComponent.getWidth()) return;
      if (point.y < 0 || point.y > myComponent.getHeight()) return;

      execute(e);
    }

    private boolean passIfNeeded(final MouseEvent e) {
      if (!UIUtil.isActionClick(e) || myMouseDeadzone.isWithin()) {
        pass(e);
        return true;
      }
      return false;
    }

  }

  private class MyMouseMotionListener extends MouseMotionAdapter {
    @Override
    public void mouseMoved(final MouseEvent e) {
      myMouseDeadzone.enter();
    }
  }

  protected abstract void execute(final MouseEvent e);

  protected void pass(MouseEvent e) {

  }

}
