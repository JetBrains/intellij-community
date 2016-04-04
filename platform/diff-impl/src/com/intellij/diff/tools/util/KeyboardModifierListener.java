/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.tools.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
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

  @Nullable private Window myWindow;

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
    IdeEventQueue.getInstance().addPostprocessor(new IdeEventQueue.EventDispatcher() {
      @Override
      public boolean dispatch(AWTEvent e) {
        if (e instanceof KeyEvent) {
          onKeyEvent((KeyEvent)e);
        }
        return false;
      }
    }, disposable);

    myWindow = UIUtil.getWindow(component);
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
