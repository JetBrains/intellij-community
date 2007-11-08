/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: Anna.Kozlova
 * Date: 31-Aug-2006
 * Time: 19:20:48
 */
package com.intellij.openapi.keymap.impl.ui;

import javax.swing.*;
import java.awt.event.KeyEvent;

public class ShortcutTextField extends JTextField {
  private KeyStroke myKeyStroke;

  public ShortcutTextField() {
    enableEvents(KeyEvent.KEY_EVENT_MASK);
    setFocusTraversalKeysEnabled(false);
  }

  protected void processKeyEvent(KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED) {
      int keyCode = e.getKeyCode();
      if (
        keyCode == KeyEvent.VK_SHIFT ||
        keyCode == KeyEvent.VK_ALT ||
        keyCode == KeyEvent.VK_CONTROL ||
        keyCode == KeyEvent.VK_ALT_GRAPH ||
        keyCode == KeyEvent.VK_META
      ){
        return;
      }

      setKeyStroke(KeyStroke.getKeyStroke(keyCode, e.getModifiers()));
    }
  }

  public void setKeyStroke(KeyStroke keyStroke) {
    myKeyStroke = keyStroke;
    setText(KeyboardShortcutDialog.getTextByKeyStroke(keyStroke));
    updateCurrentKeyStrokeInfo();
  }

  protected void updateCurrentKeyStrokeInfo() {
  }

  public KeyStroke getKeyStroke() {
    return myKeyStroke;
  }
}