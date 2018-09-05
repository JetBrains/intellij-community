// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.*;

/**
 * An {@link ExecutorService} implementation which
 * delegates tasks to the EDT for execution.
 */
class EdtExecutorServiceImpl extends EdtExecutorService {
  private EdtExecutorServiceImpl() {
  }

  @Override
  public void execute(@NotNull Runnable command) {
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      SwingUtilities.invokeLater(command);
    }
    else {
      execute(command, application.getAnyModalityState());
    }
  }

  @Override
  public void execute(@NotNull Runnable command, @NotNull ModalityState modalityState) {
    Application application = ApplicationManager.getApplication();
    if (application == null) {
      SwingUtilities.invokeLater(command);
    }
    else {
      application.invokeLater(command, modalityState);
    }
  }

  @NotNull
  @Override
  public Future<?> submit(@NotNull Runnable command, @NotNull ModalityState modalityState) {
    RunnableFuture<?> future = newTaskFor(command, null);
    execute(future, modalityState);
    return future;
  }

  @NotNull
  @Override
  public <T> Future<T> submit(@NotNull Callable<T> task, @NotNull ModalityState modalityState) {
    RunnableFuture<T> future = newTaskFor(task);
    execute(future, modalityState);
    return future;
  }

  @Override
  public void shutdown() {
    AppScheduledExecutorService.error();
  }

  @NotNull
  @Override
  public List<Runnable> shutdownNow() {
    return AppScheduledExecutorService.error();
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return false;
  }

  @Override
  public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) {
    AppScheduledExecutorService.error();
    return false;
  }

  static final EdtExecutorService INSTANCE = new EdtExecutorServiceImpl();
}
