// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.keymap.KeymapUtil;
import org.jetbrains.annotations.NotNull;
import sun.awt.AppContext;

import javax.swing.FocusManager;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
/**
 * We extend the obsolete {@link DefaultFocusManager} class here instead of {@link KeyboardFocusManager} to prevent unwanted overwriting of
 * the default focus traversal policy by careless clients. In case they use the obsolete {@link FocusManager#getCurrentManager} method
 * instead of {@link KeyboardFocusManager#getCurrentKeyboardFocusManager()}, the former will override the default focus traversal policy,
 * if current focus manager doesn't extend {@link FocusManager}. We choose to extend {@link DefaultFocusManager}, not just
 * {@link FocusManager} for the reasons described in {@link DelegatingDefaultFocusManager}'s javadoc - just in case some legacy code expects
 * it.
 */
final class IdeKeyboardFocusManager extends DefaultFocusManager /* see javadoc above */ {
  private static final Logger LOG = Logger.getInstance(IdeKeyboardFocusManager.class);

  // Don't inline this field, it's here to prevent policy override by parent's constructor. Don't make it final either.
  @SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal"}) private boolean parentConstructorExecuted = true;

  @Override
  public boolean dispatchEvent(AWTEvent e) {
    if (EventQueue.isDispatchThread()) {
      boolean[] result = {false};
      IdeEventQueue.performActivity(e, () -> result[0] = super.dispatchEvent(e));
      return result[0];
    }
    else {
      return super.dispatchEvent(e);
    }
  }

  @Override
  public void setDefaultFocusTraversalPolicy(FocusTraversalPolicy defaultPolicy) {
    if (parentConstructorExecuted) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("setDefaultFocusTraversalPolicy: " + defaultPolicy, new Throwable());
      }
      super.setDefaultFocusTraversalPolicy(defaultPolicy);
    }
  }

  @Override
  public boolean postProcessKeyEvent(KeyEvent e) {
    if (!e.isConsumed() &&
        KeymapUtil.isEventForAction(e, IdeActions.ACTION_FOCUS_EDITOR) &&
        EditorsSplitters.activateEditorComponentOnEscape(e.getComponent())) {
      e.consume();
    }
    Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    if (focusOwner == null || focusOwner instanceof Window) {
      // Swing doesn't process key bindings when there's no focus component,
      // or when focus component is a window (as window classes don't inherit from JComponent),
      // but WHEN_IN_FOCUSED_WINDOW bindings (e.g. main menu accelerators) make sense even in this case.
      SwingUtilities.processKeyBindings(e);
    }
    return super.postProcessKeyEvent(e);
  }

  @NotNull
  static IdeKeyboardFocusManager replaceDefault() {
    KeyboardFocusManager kfm = getCurrentKeyboardFocusManager();
    IdeKeyboardFocusManager ideKfm = new IdeKeyboardFocusManager();
    for (PropertyChangeListener l : kfm.getPropertyChangeListeners()) {
      ideKfm.addPropertyChangeListener(l);
    }
    AppContext.getAppContext().put(KeyboardFocusManager.class, ideKfm);
    return (IdeKeyboardFocusManager)getCurrentKeyboardFocusManager();
  }
}
