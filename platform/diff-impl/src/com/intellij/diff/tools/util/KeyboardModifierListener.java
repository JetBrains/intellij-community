// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util;

import com.intellij.codeWithMe.ClientId;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;

public class KeyboardModifierListener implements Disposable {
  private boolean myShiftPressed;
  private boolean myCtrlPressed;
  private boolean myAltPressed;

  private @Nullable Window myWindow;

  private final WindowFocusListener myWindowFocusListener = new WindowFocusListener() {
    @Override
    public void windowGainedFocus(WindowEvent e) {
      resetState();
    }

    @Override
    public void windowLostFocus(WindowEvent e) {
      resetState();
    }
  };

  public void init(@NotNull JComponent component, @NotNull Disposable disposable) {
    assert myWindow == null;

    Disposer.register(disposable, this);

    // we can use KeyListener on Editors, but Ctrl+Click will not work with focus in other place.
    // ex: commit dialog with focus in commit message
    IdeEventQueue.getInstance().addPostprocessor(e -> {
      if (e instanceof KeyEvent) {
        onKeyEvent((KeyEvent)e);
      }
      return false;
    }, disposable);

    myWindow = ComponentUtil.getWindow(component);
    if (myWindow != null) {
      myWindow.addWindowFocusListener(myWindowFocusListener);
    }
  }

  @Override
  public void dispose() {
    if (myWindow != null) {
      myWindow.removeWindowFocusListener(myWindowFocusListener);
      myWindow = null;
    }
  }

  private void onKeyEvent(KeyEvent e) {
    if (!ClientId.isCurrentlyUnderLocalId()) {
      return;
    }

    final int keyCode = e.getKeyCode();
    if (keyCode == KeyEvent.VK_SHIFT) {
      myShiftPressed = e.getID() == KeyEvent.KEY_PRESSED;
      onModifiersChanged();
    }
    if (keyCode == KeyEvent.VK_CONTROL) {
      myCtrlPressed = e.getID() == KeyEvent.KEY_PRESSED;
      onModifiersChanged();
    }
    if (keyCode == KeyEvent.VK_ALT) {
      myAltPressed = e.getID() == KeyEvent.KEY_PRESSED;
      onModifiersChanged();
    }
  }

  private void resetState() {
    myShiftPressed = false;
    myAltPressed = false;
    myCtrlPressed = false;
    onModifiersChanged();
  }

  public boolean isShiftPressed() {
    return myShiftPressed;
  }

  public boolean isCtrlPressed() {
    return myCtrlPressed;
  }

  public boolean isAltPressed() {
    return myAltPressed;
  }

  public void onModifiersChanged() {
  }
}
