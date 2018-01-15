/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.IdePopupEventDispatcher;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Objects;

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

  @Override
  public boolean dispatch(@NotNull final AWTEvent e) {
    LOG.assertTrue(isPopupActive());

    if (e.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
        if (!isPopupActive()) return false;

        boolean shouldCloseAllPopup = false;

        Window focused = ((WindowEvent)e).getOppositeWindow();
        if (focused == null) {
          focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        }

        if (focused == null) {
          shouldCloseAllPopup = true;
        }

        Component ultimateParentForFocusedComponent = UIUtil.findUltimateParent(focused);
        Window sourceWindow = ((WindowEvent)e).getWindow();
        Component ultimateParentForEventWindow = UIUtil.findUltimateParent(sourceWindow);

        if (!shouldCloseAllPopup && ultimateParentForEventWindow == null || ultimateParentForFocusedComponent == null) {
          shouldCloseAllPopup = true;
        }

        if (!shouldCloseAllPopup && ultimateParentForEventWindow instanceof IdeFrameEx) {
          IdeFrameEx ultimateParentWindowForEvent = ((IdeFrameEx)ultimateParentForEventWindow);
          if (ultimateParentWindowForEvent.isInFullScreen()
              && !ultimateParentForFocusedComponent.equals(ultimateParentForEventWindow)) {
            shouldCloseAllPopup = true;
          }
          else if (focused instanceof Dialog && ((Dialog)focused).isModal()) {
            // close all popups except one that is opening a modal dialog
            closeAllPopups(true, focused.getOwner());
            return false;
          }
        }

        if (shouldCloseAllPopup) {
          closeAllPopups();
        }
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

  public boolean closeAllPopups(boolean forceRestoreFocus) {
    return closeAllPopups(forceRestoreFocus, null);
  }

  private boolean closeAllPopups(boolean forceRestoreFocus, Window window) {
    if (myDispatchStack.size() == 0) return false;

    boolean closed = true;
    for (IdePopupEventDispatcher each : myDispatchStack) {
      if (window != null && !(window instanceof Frame) && window == UIUtil.getWindow(each.getComponent())) {
        // do not close a heavyweight popup that is opened in the specified window
        continue;
      }
      if (forceRestoreFocus) {
        each.setRestoreFocusSilentely();
      }
      closed &= each.close();
    }

    return closed;
  }

  public boolean closeAllPopups() {
    return closeAllPopups(true);
  }

  public boolean requestDefaultFocus(boolean forced) {
    if (!isPopupActive()) return false;

    return myDispatchStack.get(myDispatchStack.size() - 1).requestFocus();
  }

  public boolean isPopupWindow(Window w) {
    return myDispatchStack.stream()
             .flatMap(IdePopupEventDispatcher::getPopupStream)
             .map(JBPopup::getContent)
             .filter(Objects::nonNull)
             .anyMatch(jbPopupContent -> SwingUtilities.getWindowAncestor(jbPopupContent) == w);
  }
}
