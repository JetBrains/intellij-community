package com.intellij.debugger.impl;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 27, 2004
 * Time: 12:56:52 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class InvokeAndWaitEventImpl implements InvokeAndWaitEvent{
  private boolean myOnHold = false;
  
  public synchronized final void release() {
    if (myOnHold) {
      myOnHold = false;
      notifyAll();
    }
  }

  public synchronized final void hold() {
    myOnHold = true;
  }

  public synchronized final void waitFor() {
    while (myOnHold) {
      try {
        wait();
      }
      catch (InterruptedException ignored) {
      }
    }
  }
}
