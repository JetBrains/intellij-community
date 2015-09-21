/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 4, 2003
 * Time: 12:45:56 PM
 * To change this template use Options | File Templates.
 */
public abstract class DebuggerStateManager {
  private final EventDispatcher<DebuggerContextListener> myEventDispatcher = EventDispatcher.create(DebuggerContextListener.class);

  @NotNull
  public abstract DebuggerContextImpl getContext();

  public abstract void setState(@NotNull DebuggerContextImpl context, DebuggerSession.State state, DebuggerSession.Event event, String description);

  //we allow add listeners inside DebuggerContextListener.changeEvent
  public void addListener(DebuggerContextListener listener){
    myEventDispatcher.addListener(listener);
  }

  //we allow remove listeners inside DebuggerContextListener.changeEvent
  public void removeListener(DebuggerContextListener listener){
    myEventDispatcher.removeListener(listener);
  }

  protected void fireStateChanged(@NotNull DebuggerContextImpl newContext, DebuggerSession.Event event) {
    myEventDispatcher.getMulticaster().changeEvent(newContext, event);
  }
}
