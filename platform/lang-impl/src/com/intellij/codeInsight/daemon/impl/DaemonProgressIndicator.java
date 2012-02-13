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

package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

/**
 * @author cdr
 */
public class DaemonProgressIndicator extends ProgressIndicatorBase {
  private boolean debug;

  @Override
  public synchronized void stop() {
    super.stop();
    super.cancel();
  }

  public synchronized void stopIfRunning() {
    if (isRunning()) {
      stop();
    }
    else {
      super.cancel();
    }
  }

  @Override
  public void cancel() {
    if (debug) {
      putUserData(KILL_TRACE, new Throwable("Daemon Progress Canceled"));
    }
    super.cancel();
  }

  public void cancel(@NotNull Throwable cause) {
    if (debug) {
      putUserData(KILL_TRACE, new Throwable("Daemon Progress Canceled Because of", cause));
    }
    super.cancel();
  }

  @Override
  public void start() {
    assert !isCanceled() : "canceled";
    assert !isRunning() : "running";
    assert !wasStarted() : "was started";
    super.start();
  }

  public boolean waitFor(int millisTimeout) {
    synchronized (this) {
      try {
        // we count on ProgressManagerImpl doing progress.notifyAll() on finish
        wait(millisTimeout);
      }
      catch (InterruptedException ignored) {
      }
    }
    return isCanceled();
  }

  @TestOnly
  public void setDebug(boolean debug) {
    this.debug = debug;
  }
  private static final Key<Throwable> KILL_TRACE = Key.create("KILL_TRACE");
}
