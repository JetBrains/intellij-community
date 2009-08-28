package com.intellij.history.utils;

public abstract class RunnableAdapter implements Runnable {
  public void run() {
    try {
      doRun();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public abstract void doRun() throws Exception;
}
