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
package com.intellij.util.concurrency.readwrite;

import com.intellij.openapi.application.ApplicationManager;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
      final Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(this);
      if (aTimeout > 0) future.get(aTimeout, TimeUnit.MILLISECONDS);
      else future.get();
    }
    catch (Exception e) {
      return;
    }
  }
}
