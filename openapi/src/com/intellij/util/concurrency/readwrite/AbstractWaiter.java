package com.intellij.util.concurrency.readwrite;

public class AbstractWaiter implements Runnable {

  private boolean myFinishedFlag;

  public void setFinished(boolean aFinishedFlag) {
    myFinishedFlag = aFinishedFlag;
  }

  private boolean finished() {
    return myFinishedFlag;
  }

  public void run() {
    while (!finished()) {
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
        return;
      }
    }
  }

  public void waitForCompletion() {
    waitForCompletion(0);
  }

  public void waitForCompletion(long aTimeout) {
    try {
      Thread waiter = new Thread(this);
      waiter.start();
      waiter.join(aTimeout);
    }
    catch (InterruptedException e) {
      return;
    }
  }
}
