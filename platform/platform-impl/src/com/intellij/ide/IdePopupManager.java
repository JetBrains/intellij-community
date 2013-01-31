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
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.IdePopupEventDispatcher;
import com.intellij.util.containers.ContainerUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.List;

public final class IdePopupManager implements IdeEventQueue.EventDispatcher {
  private static final Logger LOG = Logger.getInstance("com.intellij.ide.IdePopupManager");

  private final List<IdePopupEventDispatcher> myDispatchStack = ContainerUtil.createLockFreeCopyOnWriteList();

  boolean isPopupActive() {
    for (IdePopupEventDispatcher each : myDispatchStack) {
      if (each.getComponent() == null || !each.getComponent().isShowing()) {
        myDispatchStack.remove(each);
      }
    }

    return myDispatchStack.size() > 0;
  }

  public boolean dispatch(final AWTEvent e) {
    LOG.assertTrue(isPopupActive());

    if (e.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (!isPopupActive()) return;

          Window focused = ((WindowEvent)e).getOppositeWindow();
          if (focused == null) {
            focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
          }

          if (focused == null) {
            closeAllPopups();
          }
        }
      });
    }

    if (e instanceof KeyEvent || e instanceof MouseEvent) {
      for (int i = myDispatchStack.size() - 1; (i >= 0 && i < myDispatchStack.size()); i--) {
        final boolean dispatched = myDispatchStack.get(i).dispatch(e);
        if (dispatched) return true;
      }
    }

    return false;
  }

  public void push(IdePopupEventDispatcher dispatcher) {
    if (!myDispatchStack.contains(dispatcher)) {
      myDispatchStack.add(dispatcher);
    }
  }

  public void remove(IdePopupEventDispatcher dispatcher) {
    myDispatchStack.remove(dispatcher);
  }

  public boolean closeAllPopups() {
    if (myDispatchStack.size() == 0) return false;

    boolean closed = true;
    for (IdePopupEventDispatcher each : myDispatchStack) {
      closed &= each.close();
    }

    return closed;
  }

  public boolean requestDefaultFocus(boolean forced) {
    if (!isPopupActive()) return false;

    return myDispatchStack.get(myDispatchStack.size() - 1).requestFocus();
  }
}
