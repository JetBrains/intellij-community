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
package org.jetbrains.jps.api;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;

/**
 * Makes TaskFuture from the supplied Future
 */
public class TaskFutureAdapter<T> implements TaskFuture<T> {
  @NotNull private final Future<T> myFuture;

  public TaskFutureAdapter(@NotNull Future<T> future) {
    myFuture = future;
  }

  @Override
  public void waitFor() {
    try {
      get();
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
    catch (CancellationException ignored) {
    }
  }

  @Override
  public boolean waitFor(long timeout, TimeUnit unit) {
    try {
      get(timeout, unit);
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
    catch (TimeoutException ignored) {
    }
    catch (CancellationException ignored) {
    }
    return isDone();
  }

  // delegates
  @Override
  public boolean cancel(boolean mayInterruptIfRunning) {
    return myFuture.cancel(mayInterruptIfRunning);
  }

  @Override
  public boolean isCancelled() {
    return myFuture.isCancelled();
  }

  @Override
  public boolean isDone() {
    return myFuture.isDone();
  }

  @Override
  public T get() throws InterruptedException, ExecutionException {
    return myFuture.get();
  }

  @Override
  public T get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return myFuture.get(timeout, unit);
  }
}
