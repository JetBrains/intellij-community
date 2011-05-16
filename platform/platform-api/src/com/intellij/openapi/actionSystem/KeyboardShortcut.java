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

import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * A keyboard shortcut, which can consist of one or two invidivual key strokes.
 */

public final class KeyboardShortcut extends Shortcut {
  private final KeyStroke myFirstKeyStroke;
  private final KeyStroke mySecondKeyStroke;

  /**
   * @throws IllegalArgumentException if <code>firstKeyStroke</code> is <code>null</code>
   */
  public KeyboardShortcut(@NotNull KeyStroke firstKeyStroke, KeyStroke secondKeyStroke) {
    myFirstKeyStroke = firstKeyStroke;
    mySecondKeyStroke = secondKeyStroke;
  }

  public KeyStroke getFirstKeyStroke() {
    return myFirstKeyStroke;
  }

  public KeyStroke getSecondKeyStroke() {
    return mySecondKeyStroke;
  }

  public int hashCode() {
    int hashCode = myFirstKeyStroke.hashCode();
    if (mySecondKeyStroke != null) {
      hashCode += mySecondKeyStroke.hashCode();
    }
    return hashCode;
  }

  public boolean equals(Object obj) {
    if (!(obj instanceof KeyboardShortcut)) {
      return false;
    }
    KeyboardShortcut second = (KeyboardShortcut)obj;
    if (!Comparing.equal(myFirstKeyStroke, second.myFirstKeyStroke)) {
      return false;
    }
    if (!Comparing.equal(mySecondKeyStroke, second.mySecondKeyStroke)) {
      return false;
    }
    return true;
  }

  public boolean isKeyboard() {
    return true;
  }

  public boolean startsWith(final Shortcut sc) {
    if (sc instanceof KeyboardShortcut) {
      final KeyboardShortcut other = (KeyboardShortcut)sc;
      return myFirstKeyStroke.equals(other.myFirstKeyStroke) && (other.mySecondKeyStroke == null || other.mySecondKeyStroke.equals(mySecondKeyStroke)); 
    }
    else {
      return false;
    }
  }

  public static KeyboardShortcut fromString(String s) {
    return new KeyboardShortcut(KeyStroke.getKeyStroke(s), null);
  }
}
