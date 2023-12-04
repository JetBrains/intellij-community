// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher;
import com.intellij.openapi.ui.popup.IdePopupEventDispatcher;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.jcef.JBCefBrowserBase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.List;

public final class IdePopupManager implements IdeEventQueue.EventDispatcher {
  private static final Logger LOG = Logger.getInstance(IdePopupManager.class);

  private final List<IdePopupEventDispatcher> myDispatchStack = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myIgnoreNextKeyTypedEvent;

  boolean isPopupActive() {
    for (IdePopupEventDispatcher each : myDispatchStack) {
      if (each.getComponent() == null || !each.getComponent().isShowing()) {
        myDispatchStack.remove(each);
      }
    }

    return !myDispatchStack.isEmpty();
  }

  @Override
  public boolean dispatch(final @NotNull AWTEvent e) {
    LOG.assertTrue(isPopupActive());

    if (e.getID() == WindowEvent.WINDOW_LOST_FOCUS || e.getID() == WindowEvent.WINDOW_DEACTIVATED) {
      if (IdeEventQueueKt.getSkipWindowDeactivationEvents()) {
        LOG.warn("Skipped " + e);
        return false;
      }

      if (StartupUiUtil.isWaylandToolkit()) {
        // Reasons for skipping 'focus lost'-like events on Wayland:
        // - When a new popup window appears, the main frame looses focus, but the "opposite window"
        //   for that event is null (because Wayland); this can be solved by waiting a bit
        //   (several hundreds ms) for
        //   KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow()
        //   to become non-null.
        // - When a (popup) window is dragged, it also looses focus (because Wayland); this one is
        //   not solvable because there's no guarantee that the focus will get back, nor is there
        //   a notification that the drag is actually happening.
        return false;
      }

      if (!isPopupActive()) return false;

      Window sourceWindow = ((WindowEvent)e).getWindow();
      if (SystemInfo.isLinux && !sourceWindow.isShowing()) {
        // Ignore focusLost/deactivated events caused by some window closing on Linux.
        // Normally, in such cases another IDE window is focused, and 'opposite' event property should point to that window.
        // On Linux however, due to current JDK implementation, 'opposite' property is null in this case,
        // and the following code mistakenly assumes focus is transferred to another application.
        return false;
      }

      Window focusedWindow = ((WindowEvent)e).getOppositeWindow();
      maybeCloseAllPopups(focusedWindow, sourceWindow);
      return false;
    }
    else if (e instanceof KeyEvent keyEvent) {
      // the following is copied from IdeKeyEventDispatcher
      Object source = keyEvent.getSource();
      if (myIgnoreNextKeyTypedEvent) {
        if (KeyEvent.KEY_TYPED == e.getID()) return true;
        myIgnoreNextKeyTypedEvent = false;
      }
      else if (SystemInfo.isMac && InputEvent.ALT_DOWN_MASK == keyEvent.getModifiersEx() &&
               Registry.is("ide.mac.alt.mnemonic.without.ctrl") && source instanceof Component) {
        // the myIgnoreNextKeyTypedEvent changes event processing to support Alt-based mnemonics on Mac only
        if (KeyEvent.KEY_TYPED == e.getID() && !IdeEventQueue.getInstance().isInputMethodEnabled() ||
            IdeKeyEventDispatcher.hasMnemonicInWindow((Component)source, keyEvent)) {
          myIgnoreNextKeyTypedEvent = true;
          return false;
        }
      }
    }

    if (e instanceof KeyEvent || e instanceof MouseEvent) {
      for (int i = myDispatchStack.size() - 1; i >= 0 && i < myDispatchStack.size(); i--) {
        final boolean dispatched = myDispatchStack.get(i).dispatch(e);
        if (dispatched) return true;
      }
    }

    return false;
  }

  private void maybeCloseAllPopups(Window focused, Window sourceWindow) {
    if (focused == null) {
      focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
      if (focused == null) {
        // Check if any browser is in focus (java focus can be in the process of transfer).
        JBCefBrowserBase browser = JBCefBrowserBase.getFocusedBrowser();
        if (browser != null && browser.getComponent() != null) {
          focused = SwingUtilities.getWindowAncestor(browser.getComponent());
        }
      }
    }

    Component ultimateParentForFocusedComponent = UIUtil.findUltimateParent(focused);
    Component ultimateParentForEventWindow = UIUtil.findUltimateParent(sourceWindow);

    boolean shouldCloseAllPopup = false;
    if (ultimateParentForEventWindow == null || ultimateParentForFocusedComponent == null) {
      shouldCloseAllPopup = true;
    }

    if (!shouldCloseAllPopup && ultimateParentForEventWindow instanceof IdeFrame ultimateParentWindowForEvent) {
      if (ultimateParentWindowForEvent.isInFullScreen()
          && !ultimateParentForFocusedComponent.equals(ultimateParentForEventWindow)) {
        shouldCloseAllPopup = true;
      }
    }

    if (shouldCloseAllPopup) {
      closeAllPopups();
    }
  }

  public void push(IdePopupEventDispatcher dispatcher) {
    myDispatchStack.remove(dispatcher);
    myDispatchStack.add(dispatcher);
  }

  public void remove(IdePopupEventDispatcher dispatcher) {
    myDispatchStack.remove(dispatcher);
  }

  public boolean closeAllPopups(boolean forceRestoreFocus) {
    if (myDispatchStack.isEmpty()) return false;

    boolean closed = true;
    for (IdePopupEventDispatcher each : myDispatchStack) {
      if (forceRestoreFocus) {
        each.setRestoreFocusSilently();
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
             .filter(popup->!popup.isDisposed())
             .map(JBPopup::getContent)
             .anyMatch(jbPopupContent -> SwingUtilities.getWindowAncestor(jbPopupContent) == w);
  }
}
