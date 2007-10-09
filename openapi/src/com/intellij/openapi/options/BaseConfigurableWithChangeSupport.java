/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.openapi.options;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.ArrayList;

public abstract class BaseConfigurableWithChangeSupport extends BaseConfigurable {
  private ArrayList<ChangeListener> myListeners = new ArrayList<ChangeListener>();

  public void setModified(final boolean modified) {
    fireStateChanged();
    super.setModified(modified);
  }

  public void addChangeListener(final ChangeListener listener) {
    myListeners.add(listener);
  }

  public void fireStateChanged() {
    final ChangeEvent event = new ChangeEvent(this);
    final ChangeListener[] listeners = myListeners.toArray(new ChangeListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      final ChangeListener listener = listeners[i];
      listener.stateChanged(event);
    }
  }
}