/*
 * @author max
 */
package com.intellij.openapi.util.process;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class InterruptibleActivity {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.process.InterruptibleActivity");

  private volatile boolean myIsTouched = true;
  private final long myTimeout;
  private final TimeUnit myTimeUnit;

  protected InterruptibleActivity(final long timeout, TimeUnit timeUnit) {
    myTimeout = timeout;
    myTimeUnit = timeUnit;
  }

  protected final void touch() {
    myIsTouched = true;
  }

  private boolean isTouched() {
    boolean touched = myIsTouched;
    myIsTouched = false;
    return touched;
  }

  protected abstract void start();

  protected abstract void interrupt();

  protected abstract int processTimeout();

  public final int execute() {
    final Application application = ApplicationManager.getApplication();
    LOG.assertTrue(!application.isDispatchThread(), "InterruptibleActivity is supposed to be lengthy thus must not block Swing UI thread");

    final Future<?> future = application.executeOnPooledThread(new Runnable() {
      public void run() {
        start();
      }
    });

    final int rc = waitForFuture(future);
    if (rc != 0) {
      application.executeOnPooledThread(new Runnable() {
        public void run() {
          interrupt();
        }
      });
    }

    return rc;
  }

  private int waitForFuture(final Future<?> future) {
    while (true) {
      try {
        future.get(myTimeout, myTimeUnit);
        break;
      }
      catch (InterruptedException e) {
        LOG.error(e); // Shall not happen
      }
      catch (ExecutionException e) {
        throw new RuntimeException(e);
      }
      catch (TimeoutException e) {
        if (!isTouched()) {
          int retCode = processTimeoutInEDT();
          if (retCode != 0) return future.isDone() ? 0 : retCode;
        }
      }
    }

    return 0;
  }

  private int processTimeoutInEDT() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      return processTimeout();
    }
    else {
      final int[] retcode = new int[1];

      try {
        SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            retcode[0] = processTimeout();
          }
        });
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }

      return retcode[0];
    }
  }
}