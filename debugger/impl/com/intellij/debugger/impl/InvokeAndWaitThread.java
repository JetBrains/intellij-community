package com.intellij.debugger.impl;



/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 24, 2004
 * Time: 3:27:55 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class InvokeAndWaitThread<E extends InvokeAndWaitEvent> extends InvokeThread<E>{

  public InvokeAndWaitThread(String name) {
    super(name);
  }

  //Do not remove this code
  //Otherwise it will be impossible to override invokeLater method
  public void invokeLater(E e, Priority priority) {
    super.invokeLater(e, priority);
  }

  public void invokeAndWait(final E runnable, Priority priority) {
    runnable.hold();
    invokeLater(runnable, priority);
    runnable.waitFor();
  }
}

