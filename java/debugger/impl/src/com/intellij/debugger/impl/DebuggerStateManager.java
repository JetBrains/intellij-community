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
package com.intellij.debugger.impl;

import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

public abstract class DebuggerStateManager {
  private final EventDispatcher<DebuggerContextListener> myEventDispatcher;
  {
    // Android Studio:
    // Try to not bomb during startup if running on a JRE such that
    // we can make it far enough that the SystemHealthDetector kicks in
    // https://code.google.com/p/android/issues/detail?id=225130
    EventDispatcher<DebuggerContextListener> dispatcher;
    try {
      dispatcher = EventDispatcher.create(DebuggerContextListener.class);
    } catch (Throwable t) {
      // We've already warned in the health detector
      dispatcher = null;
    }
    myEventDispatcher = dispatcher;
  }

  @NotNull
  public abstract DebuggerContextImpl getContext();

  public abstract void setState(@NotNull DebuggerContextImpl context, DebuggerSession.State state, DebuggerSession.Event event, String description);

  //we allow add listeners inside DebuggerContextListener.changeEvent
  public void addListener(DebuggerContextListener listener){
    if (myEventDispatcher == null) {
      return;
    }
    myEventDispatcher.addListener(listener);
  }

  //we allow remove listeners inside DebuggerContextListener.changeEvent
  public void removeListener(DebuggerContextListener listener){
    if (myEventDispatcher == null) {
      return;
    }
    myEventDispatcher.removeListener(listener);
  }

  protected void fireStateChanged(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
    if (myEventDispatcher == null) {
      return;
    }
    myEventDispatcher.getMulticaster().changeEvent(newContext, event);
  }

  void dispose() {
    myEventDispatcher.getListeners().clear();
  }
}
