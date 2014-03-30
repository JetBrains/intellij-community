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
package com.intellij.openapi.options;

import com.intellij.util.EventDispatcher;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public abstract class BaseConfigurableWithChangeSupport extends BaseConfigurable {
  private final EventDispatcher<ChangeListener> myDispatcher = EventDispatcher.create(ChangeListener.class);

  public void setModified(boolean modified) {
    fireStateChanged();
    super.setModified(modified);
  }

  public void addChangeListener(ChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeChangeListener(ChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  public void fireStateChanged() {
    myDispatcher.getMulticaster().stateChanged(new ChangeEvent(this));
  }
}
