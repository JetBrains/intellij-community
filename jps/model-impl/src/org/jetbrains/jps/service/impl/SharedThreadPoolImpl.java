// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.service.impl;

import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.service.SharedThreadPool;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

public final class SharedThreadPoolImpl extends SharedThreadPool {
  private final ExecutorService myService = Executors.newCachedThreadPool(ConcurrencyUtil.newNamedThreadFactory("JPS thread pool", true, Thread.NORM_PRIORITY));

  @Override
  public void execute(@NotNull Runnable command) {
    myService.execute(command);
  }

  @NotNull
  @Override
  public Future<?> executeOnPooledThread(@NotNull final Runnable action) {
    return myService.submit(() -> {
      try {
        action.run();
      }
      finally {
        Thread.interrupted(); // reset interrupted status
      }
    });
  }

  @Override
  public void shutdown() {
    myService.shutdown();
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    return myService.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return myService.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return myService.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
    return myService.awaitTermination(timeout, unit);
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Callable<T> task) {
    return myService.submit(task);
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Runnable task, T result) {
    return myService.submit(task, result);
  }

  @NotNull
  @Override
  public Future<?> submit(@NotNull Runnable task) {
    return myService.submit(task);
  }

  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return myService.invokeAll(tasks);
  }

  @NotNull
  @Override
  public <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks,
                                       long timeout,
                                       @NotNull TimeUnit unit) throws InterruptedException {
    return myService.invokeAll(tasks, timeout, unit);
  }

  @NotNull
  @Override
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return myService.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks,
                         long timeout,
                         @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return myService.invokeAny(tasks, timeout, unit);
  }
}
