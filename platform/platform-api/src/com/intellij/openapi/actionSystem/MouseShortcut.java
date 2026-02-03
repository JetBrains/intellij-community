// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.util.BitUtil;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * A mouse shortcut, which can consist of a specific mouse button, click count and modifier keys
 * (Shift, Ctrl or Alt).
 */
public class MouseShortcut extends Shortcut {
  public static final int BUTTON_WHEEL_UP = 143;
  public static final int BUTTON_WHEEL_DOWN = 142;
  private final int myButton;
  @JdkConstants.InputEventMask private final int myModifiers;
  private final int myClickCount;

  public static int getButton(MouseEvent event) {
    if (event instanceof MouseWheelEvent wheel) {
      return 0 < wheel.getPreciseWheelRotation()
             ? BUTTON_WHEEL_DOWN
             : BUTTON_WHEEL_UP;
    }
    return event.getButton();
  }

  /**
   * @param button Mouse buttons MouseEvent.BUTTON_LEFT, MouseEvent.BUTTON2, etc...
   * @param modifiers modifiersEx masks like InputEvent.ALT_DOWN_MASK and so on...
   * @param clickCount click count from the MouseEvent that caused the MouseShortcut creation
   */
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

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null || getClass() != obj.getClass()) return false;

    MouseShortcut other = (MouseShortcut) obj;

    return myButton == other.myButton
           && myModifiers == other.myModifiers
           && myClickCount == other.myClickCount;
  }

  @Override
  public int hashCode() {
    return myButton + myModifiers + myClickCount;
  }

  @JdkConstants.InputEventMask
  private static int mapOldModifiers(@JdkConstants.InputEventMask int modifiers) {
    if (BitUtil.isSet(modifiers, InputEvent.SHIFT_MASK)) {
      modifiers |= InputEvent.SHIFT_DOWN_MASK;
    }
    if (BitUtil.isSet(modifiers, InputEvent.ALT_MASK)) {
      modifiers |= InputEvent.ALT_DOWN_MASK;
    }
    if (BitUtil.isSet(modifiers, InputEvent.ALT_GRAPH_MASK)) {
      modifiers |= InputEvent.ALT_GRAPH_DOWN_MASK;
    }
    if (BitUtil.isSet(modifiers, InputEvent.CTRL_MASK)) {
      modifiers |= InputEvent.CTRL_DOWN_MASK;
    }
    if (BitUtil.isSet(modifiers, InputEvent.META_MASK)) {
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
  public boolean startsWith(final @NotNull Shortcut sc) {
    return equals(sc);
  }

  @Override
  public String toString() {
    return "button=" + myButton + " clickCount=" + myClickCount + " modifiers=" + myModifiers;
  }
}
