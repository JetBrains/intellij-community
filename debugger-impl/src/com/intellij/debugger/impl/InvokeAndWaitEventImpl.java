package com.intellij.debugger.impl;

import com.intellij.util.concurrency.Semaphore;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Feb 27, 2004
 * Time: 12:56:52 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class InvokeAndWaitEventImpl extends Semaphore implements InvokeAndWaitEvent{

  public final void release() {
    up();
  }

  public final void hold() {
    down();
  }

  public final void down() {
    super.down();
  }

  public final void up() {
    super.up();
  }

  public final void waitFor() {
    super.waitFor();
  }
}
