/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
*/
@SuppressWarnings({"UnusedDeclaration", "SSBasedInspection"})
public class TimedOutCallback extends ActionCallback implements Runnable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.ActionCallback.TimedOutCallback");

  private Throwable myAllocation;
  private String myMessage;
  private SimpleTimerTask myTask;
  private boolean myShouldDumpError;

  public TimedOutCallback(final long timeOut, String message, Throwable allocation, boolean isEdt) {
    scheduleCheck(timeOut, message, allocation, isEdt);
  }

  public TimedOutCallback(int countToDone, long timeOut, String message, Throwable allocation, boolean isEdt) {
    super(countToDone);
    scheduleCheck(timeOut, message, allocation, isEdt);
  }

  private void scheduleCheck(final long timeOut, final String message, Throwable allocation, final boolean isEdt) {
    myMessage = message;
    myAllocation = allocation;
    final long current = System.currentTimeMillis();
    myTask = SimpleTimer.getInstance().setUp(new Runnable() {
      @Override
      public void run() {
        myShouldDumpError = System.currentTimeMillis() - current > timeOut; //double check is necessary :-(
        if (isEdt) {
          SwingUtilities.invokeLater(TimedOutCallback.this);
        } else {
          TimedOutCallback.this.run();
        }
      }
    }, timeOut);
  }

  @Override
  public final void run() {
    if (!isProcessed()) {
      setRejected();

      if (myShouldDumpError) {
        dumpError();
      }

      onTimeout();
    }
  }

  protected void dumpError() {
    if (myAllocation != null) {
      LOG.error(myMessage, myAllocation);
    } else {
      LOG.error(myMessage);
    }
  }

  public String getMessage() {
    return myMessage;
  }

  public Throwable getAllocation() {
    return myAllocation;
  }

  @Override
  public void dispose() {
    super.dispose();
    myTask.cancel();
  }

  protected void onTimeout() {
  }
}
