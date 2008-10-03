/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.GuiUtils;

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

  public final void touch() {
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

    /* TODO: uncomment assertion when problems in Perforce plugin are fixed.
    LOG.assertTrue(!application.isDispatchThread(), "InterruptibleActivity is supposed to be lengthy thus must not block Swing UI thread");
    */

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