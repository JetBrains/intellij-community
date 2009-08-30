package com.intellij.debugger.impl;



/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 24, 2004
 * Time: 3:27:55 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class InvokeAndWaitThread<E extends DebuggerTask> extends InvokeThread<E>{

  public InvokeAndWaitThread() {
    super();
  }

  //Do not remove this code
  //Otherwise it will be impossible to override schedule method
  public void schedule(E e) {
    super.schedule(e);
  }

  public void pushBack(E e) {
    super.pushBack(e);
  }

  public void invokeAndWait(final E runnable) {
    runnable.hold();
    schedule(runnable);
    runnable.waitFor();
  }
}

