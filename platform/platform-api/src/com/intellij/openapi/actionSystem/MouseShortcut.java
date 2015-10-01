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
package com.intellij.openapi.actionSystem;

import org.intellij.lang.annotations.JdkConstants;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * A mouse shortcut, which can consist of a specific mouse button, click count and modifier keys
 * (Shift, Ctrl or Alt).
 */
public final class MouseShortcut extends Shortcut {
  public static final int BUTTON_WHEEL_UP = 143;
  public static final int BUTTON_WHEEL_DOWN = 142;
  private final int myButton;
  @JdkConstants.InputEventMask private final int myModifiers;
  private final int myClickCount;

  public static int getButton(MouseEvent event) {
    if (event instanceof MouseWheelEvent) {
      MouseWheelEvent wheel = (MouseWheelEvent)event;
      return 0 < wheel.getWheelRotation()
             ? BUTTON_WHEEL_DOWN
             : BUTTON_WHEEL_UP;
    }
    return event.getButton();
  }

  public MouseShortcut(int button, @JdkConstants.InputEventMask int modifiers, int clickCount) {
    myButton = button;
    // TODO[vova] check modifiers?
    myModifiers = mapOldModifiers(modifiers);
    if (clickCount < 1) {
      throw new IllegalArgumentException("wrong clickCount: " + clickCount);
    }
    myClickCount = clickCount;
  }

  public int getButton() {
    return myButton;
  }

  @JdkConstants.InputEventMask
  public int getModifiers() {
    return myModifiers;
  }

  public int getClickCount() {
    return myClickCount;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof MouseShortcut)) {
      return false;
    }
    MouseShortcut shortcut = (MouseShortcut)obj;
    return myButton == shortcut.myButton && myModifiers == shortcut.myModifiers && myClickCount ==
                                                                                   shortcut.myClickCount;
  }

  public int hashCode() {
    return myButton + myModifiers + myClickCount;
  }

  @JdkConstants.InputEventMask
  private static int mapOldModifiers(@JdkConstants.InputEventMask int modifiers) {
    if ((modifiers & InputEvent.SHIFT_MASK) != 0) {
      modifiers |= InputEvent.SHIFT_DOWN_MASK;
    }
    if ((modifiers & InputEvent.ALT_MASK) != 0) {
      modifiers |= InputEvent.ALT_DOWN_MASK;
    }
    if ((modifiers & InputEvent.ALT_GRAPH_MASK) != 0) {
      modifiers |= InputEvent.ALT_GRAPH_DOWN_MASK;
    }
    if ((modifiers & InputEvent.CTRL_MASK) != 0) {
      modifiers |= InputEvent.CTRL_DOWN_MASK;
    }
    if ((modifiers & InputEvent.META_MASK) != 0) {
      modifiers |= InputEvent.META_DOWN_MASK;
    }

    modifiers &= InputEvent.SHIFT_DOWN_MASK
                 | InputEvent.ALT_DOWN_MASK
                 | InputEvent.ALT_GRAPH_DOWN_MASK
                 | InputEvent.CTRL_DOWN_MASK
                 | InputEvent.META_DOWN_MASK;

    return modifiers;
  }

  @Override
  public boolean isKeyboard() {
    return false;
  }

  @Override
  public boolean startsWith(final Shortcut sc) {
    return equals(sc);
  }

  @Override
  public String toString() {
    return "button=" + myButton + " clickCount=" + myClickCount + " modifiers=" + myModifiers;
  }
}
