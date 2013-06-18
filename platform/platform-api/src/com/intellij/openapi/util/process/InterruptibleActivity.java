/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * @author max
 */
package com.intellij.openapi.util.process;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.ui.GuiUtils;
import com.intellij.util.concurrency.Semaphore;

import java.util.concurrent.TimeUnit;

public abstract class InterruptibleActivity {
  private volatile boolean myIsTouched = true;
  private final long myTimeout;
  private final TimeUnit myTimeUnit;

  protected InterruptibleActivity(final long timeout, TimeUnit timeUnit) {
    myTimeout = timeout;
    myTimeUnit = timeUnit;
  }

  public final void touch() {
    myIsTouched = true;
  }

  protected abstract void start();

  protected abstract void interrupt();

  protected abstract int processTimeout();

  public final int execute() {
    final Application application = ApplicationManager.getApplication();

    /* TODO: uncomment assertion when problems in Perforce plugin are fixed.
    LOG.assertTrue(!application.isDispatchThread(), "InterruptibleActivity is supposed to be lengthy thus must not block Swing UI thread");
    */

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    application.executeOnPooledThread(new Runnable() {
      public void run() {
        try {
          start();
        }
        finally {
          semaphore.up();
        }
      }
    });

    final int rc = waitForSemaphore(semaphore);
    if (rc != 0) {
      application.executeOnPooledThread(new Runnable() {
        public void run() {
          interrupt();
        }
      });
    }

    return rc;
  }

  private int waitForSemaphore(final Semaphore semaphore) {
    long timeoutMs = myTimeUnit.toMillis(myTimeout);
    long lastActiveMoment = System.currentTimeMillis();
    while (true) {
      long current = System.currentTimeMillis();
      if (myIsTouched) {
        myIsTouched = false;
        lastActiveMoment = current;
      }
      
      long idleTime = current - lastActiveMoment;
      if (idleTime > timeoutMs) {
        int retCode = processTimeoutInEDT();
        return semaphore.waitFor(0) ? 0 : retCode;
      }
      
      ProgressManager.checkCanceled();

      if (semaphore.waitFor(Math.min(500, timeoutMs - idleTime))) {
        return 0;
      }
    }
  }

  protected int processTimeoutInEDT() {
    final int[] retcode = new int[1];

    try {
      GuiUtils.runOrInvokeAndWait(new Runnable() {
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