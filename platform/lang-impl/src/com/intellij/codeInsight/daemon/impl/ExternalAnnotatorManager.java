// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
final class ExternalAnnotatorManager implements Disposable {
  static ExternalAnnotatorManager getInstance() {
    return ApplicationManager.getApplication().getService(ExternalAnnotatorManager.class);
  }

  private final MergingUpdateQueue myExternalActivitiesQueue =
    new MergingUpdateQueue("ExternalActivitiesQueue", ApplicationManager.getApplication().isUnitTestMode() ? 0 : 300, true, MergingUpdateQueue.ANY_COMPONENT, this,
                           null, false);

  @Override
  public void dispose() {
  }

  void queue(@NotNull Update update) {
    myExternalActivitiesQueue.queue(update);
  }

  void waitForAllExecuted(long timeout, @NotNull TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    CompletableFuture<?> future = new CompletableFuture<>();
    queue(new Update(new Object()/*must not coalesce with anything in the queue*/) {
      @Override
      public void setRejected() {
        super.setRejected();
        future.cancel(false);
      }

      @Override
      public void run() {
        future.complete(null);
      }
    });
    myExternalActivitiesQueue.flush();
    future.get(timeout, unit);
  }
}
