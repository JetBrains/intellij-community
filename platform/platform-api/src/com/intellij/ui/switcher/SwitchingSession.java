/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.switcher;

import com.intellij.openapi.actionSystem.AnActionEvent;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class SwitchingSession implements KeyEventDispatcher {

  private SwitchProvider myProvider;
  private AnActionEvent myInitialEvent;
  private boolean myFinished;

  public SwitchingSession(SwitchProvider provider, AnActionEvent e) {
    myProvider = provider;
    myInitialEvent = e;

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);

  }

  public boolean dispatchKeyEvent(KeyEvent e) {
    KeyEvent event = (KeyEvent)myInitialEvent.getInputEvent();
    if ((e.getModifiers() & event.getModifiers()) == 0) {
      finish();
      return false;
    }

    return false;
  }


  public void up() {
    System.out.println("SwitchingSession.up");
  }

  public void down() {
    System.out.println("SwitchingSession.down");
  }

  public void left() {
    System.out.println("SwitchingSession.left");
  }

  public void right() {
    System.out.println("SwitchingSession.right");
  }

  private void finish() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    myFinished = true;

    System.out.println("SwitchingSession.finish");
  }

  public boolean isFinished() {
    return myFinished;
  }
}
