package com.intellij.openapi.actionSystem;

import javax.swing.*;

public class KeyboardModifierGestureShortuct extends Shortcut {

  private KeyStroke myStroke;
  private KeyboardGestureAction.Type myType;

  public static Shortcut newInstance(KeyboardGestureAction.Type type, KeyStroke stroke) {
    return null;
  }

  public KeyboardModifierGestureShortuct(final KeyStroke stroke, KeyboardGestureAction.Type type) {
    myStroke = stroke;
    myType = type;
  }

  public KeyStroke getStroke() {
    return myStroke;
  }

  public KeyboardGestureAction.Type getType() {
    return myType;
  }

  public boolean isKeyboard() {
    return true;
  }

  public boolean startsWith(final Shortcut sc) {
    if (!(sc instanceof KeyboardModifierGestureShortuct)) return false;

    final KeyboardModifierGestureShortuct other = (KeyboardModifierGestureShortuct)sc;
    if (myType.equals(other.myType)) {
      if (myStroke.getModifiers() != other.myStroke.getModifiers()) return false;
      return other.myStroke.getKeyCode() != -1 || other.myStroke.getKeyCode() == myStroke.getKeyCode();
    }

    return false;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final KeyboardModifierGestureShortuct that = (KeyboardModifierGestureShortuct)o;

    if (myStroke != null ? !myStroke.equals(that.myStroke) : that.myStroke != null) return false;
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

  public static class DblClick extends KeyboardModifierGestureShortuct {
    public DblClick(final KeyStroke stroke) {
      super(stroke, KeyboardGestureAction.Type.dblClick);
    }
  }

  public static class Hold extends KeyboardModifierGestureShortuct {
    public Hold(final KeyStroke stroke) {
      super(stroke, KeyboardGestureAction.Type.hold);
    }
  }
}