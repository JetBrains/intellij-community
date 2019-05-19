// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

// class is used during app start-up,
// so, don't want to use Guava (MoreExecutors.newDirectExecutorService()) here to avoid Guava classes loading
public final class SameThreadExecutorService extends AbstractExecutorService {
  private volatile boolean isTerminated;

  @Override
  public void shutdown() {
    isTerminated = true;
  }

  @Override
  public boolean isShutdown() {
    return isTerminated;
  }

  @Override
  public boolean isTerminated() {
    return isTerminated;
  }

  @Override
  public boolean awaitTermination(long theTimeout, @NotNull TimeUnit theUnit) {
    shutdown();
    return true;
  }

  @NotNull
  @Contract(pure = true)
  @Override
  public List<Runnable> shutdownNow() {
    return Collections.emptyList();
  }

  @Override
  public void execute(@NotNull Runnable command) {
    command.run();
  }
}
