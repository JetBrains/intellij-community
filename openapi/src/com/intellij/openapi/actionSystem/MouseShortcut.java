/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public final class MouseShortcut extends Shortcut {
  private final int myButton;
  private final int myModifiers;
  private final int myClickCount;

  public MouseShortcut(int button, int modifiers, int clickCount) {
    if (
      MouseEvent.BUTTON1 != button &&
      MouseEvent.BUTTON2 != button &&
      MouseEvent.BUTTON3 != button
    ) {
      throw new IllegalArgumentException("unknown button: " + button);
    }
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

  private static int mapOldModifiers(int modifiers) {
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
}
