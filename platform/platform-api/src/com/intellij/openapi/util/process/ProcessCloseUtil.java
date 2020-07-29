// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.process;

import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public final class ProcessCloseUtil {
  private static final long ourSynchronousWaitTimeout = 1000;
  private static final long ourAsynchronousWaitTimeout = 30 * 1000;

  public static void close(@NotNull Process process) {
    try {
      if (process.waitFor(ourSynchronousWaitTimeout, TimeUnit.MILLISECONDS)) {
        closeStreams(process);
        return;
      }
    }
    catch (InterruptedException e) {
      closeStreams(process);
      throw new RuntimeException(e);
    }

    process.destroy();

    AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
      try {
        if (process.isAlive()) {
          process.destroyForcibly();
        }
      }
      finally {
        closeStreams(process);
      }
    }, ourAsynchronousWaitTimeout, TimeUnit.MILLISECONDS);
  }

  /**
   * We need to close process streams, because on Windows (MSDN for "TerminateProcess" function):
   * "When a process terminates, its kernel object is not destroyed until all processes that have open handles to the process have released those handles."
   */
  private static void closeStreams(@NotNull Process process) {
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
  }
}
