package com.intellij.openapi.util.process;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.Semaphore;

import java.util.concurrent.Future;
import java.io.IOException;

public class ProcessCloseUtil {
  private static final long ourSynchronousWaitTimeout = 1000;
  private static final long ourAsynchronousWaitTimeout = 30 * 1000;

  private ProcessCloseUtil() {
  }

  public static void close(final Process process) {
    final Semaphore outerSemaphore = new Semaphore();
    outerSemaphore.down();

    final Application application = ApplicationManager.getApplication();
    final Future<?> future = application.executeOnPooledThread(new Runnable() {
      public void run() {
        final Semaphore semaphore = new Semaphore();
        semaphore.down();

        final Runnable closeRunnable = new Runnable() {
          public void run() {
            closeProcessImpl(process);
            semaphore.up();
          }
        };

        final Future<?> innerFuture = application.executeOnPooledThread(closeRunnable);
        semaphore.waitFor(ourAsynchronousWaitTimeout);
        if ( ! (innerFuture.isDone() || innerFuture.isCancelled())) {
          innerFuture.cancel(true); // will call interrupt()
        }

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
