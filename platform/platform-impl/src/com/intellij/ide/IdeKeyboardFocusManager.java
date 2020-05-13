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

import com.intellij.openapi.application.AccessToken;
import sun.awt.AppContext;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeListener;
import java.util.function.Consumer;

class IdeKeyboardFocusManager extends DefaultKeyboardFocusManager {

  private Consumer<KeyEvent> onTypeaheadFinished = ke -> {};

  public void setTypeaheadHandler(Consumer<KeyEvent> onTypeaheadFinished) {
    this.onTypeaheadFinished = onTypeaheadFinished;
  }

  protected Consumer<KeyEvent> getOnTypeaheadFinishedHandler () {
    return onTypeaheadFinished;
  }

  @Override
  public boolean dispatchEvent(AWTEvent e) {
    try (AccessToken ignore = (EventQueue.isDispatchThread() ? IdeEventQueue.startActivity(e) : null)) {
      return super.dispatchEvent(e);
    }
  }

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
