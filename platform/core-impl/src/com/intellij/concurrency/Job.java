/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.concurrency;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public interface Job<T> {
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

  @SuppressWarnings("unchecked")
  static <T> Job<T> nullJob() {
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