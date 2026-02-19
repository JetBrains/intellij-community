// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;

public abstract class DebuggerStateManager {
  private final EventDispatcher<DebuggerContextListener> myEventDispatcher = EventDispatcher.create(DebuggerContextListener.class);

  public abstract @NotNull DebuggerContextImpl getContext();

  public abstract void setState(@NotNull DebuggerContextImpl context,
                                DebuggerSession.State state,
                                DebuggerSession.Event event,
                                String description);

  //we allow add listeners inside DebuggerContextListener.changeEvent
  public void addListener(DebuggerContextListener listener) {
    myEventDispatcher.addListener(listener);
  }

  //we allow remove listeners inside DebuggerContextListener.changeEvent
  public void removeListener(DebuggerContextListener listener) {
    myEventDispatcher.removeListener(listener);
  }

  protected void fireStateChanged(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
    myEventDispatcher.getMulticaster().changeEvent(newContext, event);
  }

  void dispose() {
    myEventDispatcher.getListeners().clear();
  }
}
