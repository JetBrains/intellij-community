// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.concurrency;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
@ApiStatus.Internal
public interface Job {
  void cancel();

  boolean isCanceled();

  boolean isDone();

  /**
   * Waits until all work is executed.
   * Note that calling {@link #cancel()} might not lead to this method termination because the job can be in the middle of execution.
   *
   * @return true if completed
   */
  boolean waitForCompletion(int millis) throws InterruptedException;

  static Job nullJob() {
    return NULL_JOB;
  }

  @NotNull
  Job NULL_JOB = new Job() {
    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public boolean waitForCompletion(int millis) {
      return true;
    }

    @Override
    public void cancel() {
    }

    @Override
    public boolean isCanceled() {
      return true;
    }
  };
}