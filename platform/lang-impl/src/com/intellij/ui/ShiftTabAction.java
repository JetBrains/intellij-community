/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Provides Shift-Tab support in EditorTextFields which otherwise don't support this keystroke to
 * move the input focus to the previous component.
 */
@SuppressWarnings({"ComponentNotRegistered"})
public class ShiftTabAction extends AnAction {
  private static final CustomShortcutSet SHIFT_TAB;

  static {
    final KeyStroke keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.SHIFT_MASK);
    SHIFT_TAB = new CustomShortcutSet(keyStroke);
  }

  private final EditorTextField myEditor;

  private ShiftTabAction(EditorTextField editor) {
    super("Shift-Tab");
    myEditor = editor;
  }

  public void actionPerformed(AnActionEvent event) {
    Container container = myEditor.getParent();
    while (container != null && container.getFocusTraversalPolicy() == null) {
      container = container.getParent();
    }
    if (container != null) {
      final FocusTraversalPolicy ftp = container.getFocusTraversalPolicy();
      if (ftp != null) {
        final Component prev = ftp.getComponentBefore(container, myEditor);
        if (prev != null) {
          prev.requestFocus();
        }
      }
    }
  }

  /**
   * Call this method to enable Sift-Tab support for the supplied EditorTextField.
   */
  public static void attachTo(EditorTextField textField) {
    // TODO following code seems not needed due to textField.pleaseHandleShiftTab()
    new ShiftTabAction(textField).registerCustomShortcutSet(SHIFT_TAB, textField);
  }
}
