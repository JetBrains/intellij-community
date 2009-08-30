package com.intellij.debugger.impl;

import com.intellij.util.EventDispatcher;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 4, 2003
 * Time: 12:45:56 PM
 * To change this template use Options | File Templates.
 */
public abstract class DebuggerStateManager {
  private final EventDispatcher<DebuggerContextListener> myEventDispatcher = EventDispatcher.create(DebuggerContextListener.class);

  public abstract DebuggerContextImpl getContext();

  public abstract void setState(DebuggerContextImpl context, int state, int event, String description);

  //we allow add listeners inside DebuggerContextListener.changeEvent
  public void addListener(DebuggerContextListener listener){
    myEventDispatcher.addListener(listener);
  }

  //we allow remove listeners inside DebuggerContextListener.changeEvent
  public void removeListener(DebuggerContextListener listener){
    myEventDispatcher.removeListener(listener);
  }

  protected void fireStateChanged(DebuggerContextImpl newContext, int event) {
    myEventDispatcher.getMulticaster().changeEvent(newContext, event);
  }
}
