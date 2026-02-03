// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.keymap.KeymapUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;

public class KeyboardModifierGestureShortcut extends Shortcut {
  private final KeyStroke myStroke;
  private final KeyboardGestureAction.ModifierType myType;

  public static @NotNull Shortcut newInstance(KeyboardGestureAction.ModifierType type, KeyStroke stroke) {
    return switch (type) {
      case dblClick -> new DblClick(stroke);
      case hold -> new Hold(stroke);
    };
  }

  protected KeyboardModifierGestureShortcut(KeyStroke stroke, KeyboardGestureAction.ModifierType type) {
    myStroke = stroke;
    myType = type;
  }

  public KeyStroke getStroke() {
    return myStroke;
  }

  public KeyboardGestureAction.ModifierType getType() {
    return myType;
  }

  @Override
  public boolean isKeyboard() {
    return true;
  }

  @Override
  public boolean startsWith(@NotNull Shortcut shortcut) {
    if (!(shortcut instanceof KeyboardModifierGestureShortcut other)) return false;

    if (myType.equals(other.myType)) {
      if (myStroke.getModifiers() != other.myStroke.getModifiers()) {
        return false;
      }
      return other.myStroke.getKeyCode() != -1 || other.myStroke.getKeyCode() == myStroke.getKeyCode();
    }

    return false;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final KeyboardModifierGestureShortcut that = (KeyboardModifierGestureShortcut)o;

    if (!Objects.equals(myStroke, that.myStroke)) return false;
    if (myType != that.myType) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = (myStroke != null ? myStroke.hashCode() : 0);
    result = 31 * result + (myType != null ? myType.hashCode() : 0);
    return result;
  }

  public static class DblClick extends KeyboardModifierGestureShortcut {
    public DblClick(final KeyStroke stroke) {
      super(stroke, KeyboardGestureAction.ModifierType.dblClick);
    }
  }

  public static final class Hold extends KeyboardModifierGestureShortcut {
    public Hold(final KeyStroke stroke) {
      super(stroke, KeyboardGestureAction.ModifierType.hold);
    }
  }

  @Override
  public String toString() {
    String s = getType() == KeyboardGestureAction.ModifierType.dblClick ? "Press, release and hold " : "Hold ";
    s += KeymapUtil.getKeystrokeText(this.getStroke());
    return s;
  }
}
