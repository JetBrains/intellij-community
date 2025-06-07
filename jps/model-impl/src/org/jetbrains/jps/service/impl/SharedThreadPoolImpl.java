// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.service.impl;

import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.service.SharedThreadPool;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;

public final class SharedThreadPoolImpl extends SharedThreadPool {
  private final ExecutorService myService = Executors.newCachedThreadPool(ConcurrencyUtil.newNamedThreadFactory("JPS thread pool", true, Thread.NORM_PRIORITY));

  @Override
  public @NotNull ExecutorService createBoundedExecutor(@NotNull String name, int maxThreads) {
    return AppExecutorUtil.createBoundedApplicationPoolExecutor(name, this, maxThreads);
  }

  @Override
  public @NotNull Executor createCustomPriorityQueueBoundedExecutor(@NotNull String name,
                                                                    int maxThreads,
                                                                    @NotNull Comparator<? super Runnable> comparator) {
    return AppExecutorUtil.createCustomPriorityQueueBoundedApplicationPoolExecutor(name, this, maxThreads, comparator);
  }

  @Override
  public void execute(@NotNull Runnable command) {
    myService.execute(command);
  }

  @Override
  public void shutdown() {
    myService.shutdown();
  }

  @Override
  public @NotNull List<Runnable> shutdownNow() {
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

  @Override
  public @NotNull <T> Future<T> submit(@NotNull Callable<T> task) {
    return myService.submit(task);
  }

  @Override
  public @NotNull <T> Future<T> submit(@NotNull Runnable task, T result) {
    return myService.submit(task, result);
  }

  @Override
  public @NotNull Future<?> submit(@NotNull Runnable task) {
    return myService.submit(task);
  }

  @Override
  public @NotNull <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException {
    return myService.invokeAll(tasks);
  }

  @Override
  public @NotNull <T> List<Future<T>> invokeAll(@NotNull Collection<? extends Callable<T>> tasks,
                                                long timeout,
                                                @NotNull TimeUnit unit) throws InterruptedException {
    return myService.invokeAll(tasks, timeout, unit);
  }

  @Override
  public @NotNull <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
    return myService.invokeAny(tasks);
  }

  @Override
  public <T> T invokeAny(@NotNull Collection<? extends Callable<T>> tasks,
                         long timeout,
                         @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
    return myService.invokeAny(tasks, timeout, unit);
  }
}
