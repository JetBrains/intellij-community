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

import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public interface Job<T> {

  String getTitle();

  void addTask(@NotNull Callable<T> task);

  void addTask(@NotNull Runnable task, T result);

  void addTask(@NotNull Runnable task);

  List<T> scheduleAndWaitForResults() throws Throwable;

  void cancel();

  boolean isCanceled();

  void schedule();

  boolean isDone();

  void waitForCompletion(int millis) throws InterruptedException, ExecutionException, TimeoutException;

  @NotNull
  Job NULL_JOB = new Job() {
    @Override
    public boolean isDone() {
      return true;
    }

    @Override
    public void waitForCompletion(int millis) throws InterruptedException, ExecutionException, TimeoutException {

    }

    @Override
    public void cancel() {
    }

    @Override
    public String getTitle() {
      return null;
    }

    @Override
    public void addTask(@NotNull Callable task) {
      throw new IncorrectOperationException();
    }

    @Override
    public void addTask(@NotNull Runnable task, Object result) {
      throw new IncorrectOperationException();
    }

    @Override
    public void addTask(@NotNull Runnable task) {
      throw new IncorrectOperationException();
    }

    @Override
    public List scheduleAndWaitForResults() throws Throwable {
      throw new IncorrectOperationException();
    }

    @Override
    public boolean isCanceled() {
      return true;
    }

    @Override
    public void schedule() {
      throw new IncorrectOperationException();
    }
  };

}