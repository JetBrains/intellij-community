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

import org.jetbrains.annotations.NotNull;
import sun.awt.AppContext;

import java.awt.*;
import java.beans.PropertyChangeListener;

class IdeKeyboardFocusManager extends DefaultKeyboardFocusManager {
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
