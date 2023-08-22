// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * A keyboard shortcut, which can consist of one or two individual keystrokes.
 */
public final class KeyboardShortcut extends Shortcut {
  private final KeyStroke myFirstKeyStroke;
  private final KeyStroke mySecondKeyStroke;

  /**
   * @throws IllegalArgumentException if {@code firstKeyStroke} is {@code null}
   */
  public KeyboardShortcut(@NotNull KeyStroke firstKeyStroke, @Nullable KeyStroke secondKeyStroke) {
    myFirstKeyStroke = firstKeyStroke;
    mySecondKeyStroke = secondKeyStroke;
  }

  public @NotNull KeyStroke getFirstKeyStroke() {
    return myFirstKeyStroke;
  }

  public @Nullable KeyStroke getSecondKeyStroke() {
    return mySecondKeyStroke;
  }

  @Override
  public int hashCode() {
    int hashCode = myFirstKeyStroke.hashCode();
    if (mySecondKeyStroke != null) {
      hashCode += mySecondKeyStroke.hashCode();
    }
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof KeyboardShortcut second)) {
      return false;
    }
    return Comparing.equal(myFirstKeyStroke, second.myFirstKeyStroke) && Comparing.equal(mySecondKeyStroke, second.mySecondKeyStroke);
  }

  @Override
  public boolean isKeyboard() {
    return true;
  }

  @Override
  public boolean startsWith(final @NotNull Shortcut sc) {
    if (sc instanceof KeyboardShortcut other) {
      return myFirstKeyStroke.equals(other.myFirstKeyStroke) && (other.mySecondKeyStroke == null || other.mySecondKeyStroke.equals(mySecondKeyStroke));
    }
    else {
      return false;
    }
  }

  public static KeyboardShortcut fromString(@NonNls String s) {
    final KeyStroke keyStroke = KeyStroke.getKeyStroke(s);
    assert keyStroke != null : "Can't create key stroke for " + s;
    return new KeyboardShortcut(keyStroke, null);
  }

  @Override
  public @NonNls String toString() {
    return mySecondKeyStroke == null ? "[" + myFirstKeyStroke + "]" : "[" + myFirstKeyStroke + "]+[" + mySecondKeyStroke + "]";
  }
}
