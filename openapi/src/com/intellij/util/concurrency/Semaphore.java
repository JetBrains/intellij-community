/*
 * @author: Eugene Zhuravlev
 * Date: Jun 2, 2003
 * Time: 8:50:03 PM
 */
package com.intellij.util.concurrency;

import com.intellij.openapi.diagnostic.Logger;

public class Semaphore {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.concurrency.Semaphore");
  private int mySemaphore = 0;

  public synchronized void down() {
    mySemaphore++;
  }

  public synchronized void up() {
    mySemaphore--;
    if (mySemaphore == 0) {
      notifyAll();
    }
  }

  public synchronized void waitFor() {
    try {
      while (mySemaphore > 0) {
        wait();
      }
    }
    catch (InterruptedException e) {
      LOG.debug(e);
      throw new RuntimeException(e);
    }
  }

}
