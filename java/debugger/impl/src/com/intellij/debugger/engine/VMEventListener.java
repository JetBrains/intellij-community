package com.intellij.debugger.engine;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jul 15, 2003
 * Time: 6:38:23 PM
 * To change this template use Options | File Templates.
 */
public interface VMEventListener {
  //aware! called in DebuggerEventThread
  void vmEvent(com.sun.jdi.event.Event event);
}
