package com.intellij.util.concurrency;

import com.intellij.openapi.application.RuntimeInterruptedException;

public class ReentrantLock2 extends ReentrantLock implements Sync2{
  public void acquire() {
    try{
      super.acquire();
    }
    catch(InterruptedException e){
        throw new RuntimeInterruptedException(e);
    }
  }

  public boolean attempt(long msecs) {
    try{
      return super.attempt(msecs);
    }
    catch(InterruptedException e){
      throw new RuntimeInterruptedException(e);
    }
  }
}
