package com.intellij.debugger.impl;
import java.util.EventListener;


public interface DebuggerContextListener extends EventListener{
  void changeEvent(DebuggerContextImpl newContext, int event);
}
