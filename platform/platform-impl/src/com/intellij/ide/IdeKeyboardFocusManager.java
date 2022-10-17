// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.FocusManager;
import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.VetoableChangeListener;
import java.lang.reflect.Method;
import java.util.List;

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

  static void replaceDefault() {
    KeyboardFocusManager kfm = getCurrentKeyboardFocusManager();
    IdeKeyboardFocusManager ideKfm = new IdeKeyboardFocusManager();
    for (PropertyChangeListener l : kfm.getPropertyChangeListeners()) {
      ideKfm.addPropertyChangeListener(l);
    }
    for (VetoableChangeListener l : kfm.getVetoableChangeListeners()) {
      ideKfm.addVetoableChangeListener(l);
    }
    try {
      Method getDispatchersMethod = KeyboardFocusManager.class.getDeclaredMethod("getKeyEventDispatchers");
      getDispatchersMethod.setAccessible(true);
      @SuppressWarnings("unchecked") List<KeyEventDispatcher> dispatchers = (List<KeyEventDispatcher>)getDispatchersMethod.invoke(kfm);
      if (dispatchers != null) {
        for (KeyEventDispatcher d : dispatchers) {
          ideKfm.addKeyEventDispatcher(d);
        }
      }
      Method getPostProcessorsMethod = KeyboardFocusManager.class.getDeclaredMethod("getKeyEventPostProcessors");
      getPostProcessorsMethod.setAccessible(true);
      @SuppressWarnings("unchecked") List<KeyEventPostProcessor> postProcessors =
        (List<KeyEventPostProcessor>)getPostProcessorsMethod.invoke(kfm);
      if (postProcessors != null) {
        for (KeyEventPostProcessor p : postProcessors) {
          ideKfm.addKeyEventPostProcessor(p);
        }
      }
    }
    catch (Exception e) {
      LOG.error(e);
    }
    KeyboardFocusManager.setCurrentKeyboardFocusManager(ideKfm);
  }
}
