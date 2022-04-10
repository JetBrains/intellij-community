/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import sun.awt.AppContext;

import javax.swing.*;
import javax.swing.FocusManager;
import java.awt.*;
import java.beans.PropertyChangeListener;
/**
 * We extend the obsolete {@link DefaultFocusManager} class here instead of {@link KeyboardFocusManager} to prevent unwanted overwriting of
 * the default focus traversal policy by careless clients. In case they use the obsolete {@link FocusManager#getCurrentManager} method
 * instead of {@link KeyboardFocusManager#getCurrentKeyboardFocusManager()}, the former will override the default focus traversal policy,
 * if current focus manager doesn't extend {@link FocusManager}. We choose to extend {@link DefaultFocusManager}, not just
 * {@link FocusManager} for the reasons described in {@link DelegatingDefaultFocusManager}'s javadoc - just in case some legacy code expects
 * it.
 */
class IdeKeyboardFocusManager extends DefaultFocusManager /* see javadoc above */ {
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
