package com.intellij.util.concurrency.readwrite;

public abstract class ActiveRunnableWrapper implements Runnable {

  private Object myResult;

  private Throwable myException;

  public void run() {
    try {
      myResult = doRun();
    }
    catch (Throwable aThrowable) {
      myException = aThrowable;
    }
  }

  public Object getResult() {
    return myResult;
  }

  private Throwable getException() {
    return myException;
  }

  private boolean hasException() {
    return null != getException();
  }

  public void throwException() throws Throwable {
    if (hasException()) {
      throw getException();
    }
  }

  public abstract Object doRun() throws Throwable;
}
