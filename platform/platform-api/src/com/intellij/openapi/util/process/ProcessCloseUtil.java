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
package com.intellij.openapi.util.process;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.Semaphore;

import java.io.IOException;
import java.util.concurrent.Future;

public class ProcessCloseUtil {
  private static final long ourSynchronousWaitTimeout = 1000;
  private static final long ourAsynchronousWaitTimeout = 30 * 1000;

  private ProcessCloseUtil() {
  }

  public static void close(final Process process) {
    if (!process.isAlive()) return;
    
    final Semaphore outerSemaphore = new Semaphore();
    outerSemaphore.down();

    final Application application = ApplicationManager.getApplication();
    application.executeOnPooledThread(() -> {
      try {
        final Semaphore semaphore = new Semaphore();
        semaphore.down();

        final Runnable closeRunnable = () -> {
          try {
            closeProcessImpl(process);
          }
          finally {
            semaphore.up();
          }
        };

        final Future<?> innerFuture = application.executeOnPooledThread(closeRunnable);
        semaphore.waitFor(ourAsynchronousWaitTimeout);
        if ( ! (innerFuture.isDone() || innerFuture.isCancelled())) {
          innerFuture.cancel(true); // will call interrupt()
        }
      }
      finally {
        outerSemaphore.up();
      }
    });

    // just wait
    outerSemaphore.waitFor(ourSynchronousWaitTimeout);
  }

  private static void closeProcessImpl(final Process process) {
    try {
      process.getOutputStream().close();
    }
    catch (IOException e) {/**/}
    try {
      process.getInputStream().close();
    }
    catch (IOException e) {/**/}
    try {
      process.getErrorStream().close();
    }
    catch (IOException e) {/**/}
    process.destroy();
  }
}
