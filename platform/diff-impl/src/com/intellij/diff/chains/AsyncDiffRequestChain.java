// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.chains;

import com.intellij.diff.chains.SimpleDiffRequestChain.DiffRequestProducerWrapper;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.LoadingDiffRequest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventListener;

/**
 * Allows loading requests asynchronously after showing diff UI, without the need for modal progress
 *
 * @see com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain.Async
 */
public abstract class AsyncDiffRequestChain extends UserDataHolderBase implements DiffRequestSelectionChain {
  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);

  private volatile ListSelection<? extends DiffRequestProducer> myRequests = null;

  private @Nullable ProgressIndicator myIndicator;
  private int myAssignments = 0;

  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }

  public void removeListener(@NotNull Listener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public @NotNull ListSelection<? extends DiffRequestProducer> getListSelection() {
    ListSelection<? extends DiffRequestProducer> requests = myRequests;
    if (requests == null) {
      return ListSelection.createSingleton(new DiffRequestProducerWrapper(new LoadingDiffRequest()));
    }
    return requests;
  }

  @RequiresBackgroundThread
  public @NotNull ListSelection<? extends DiffRequestProducer> loadRequestsInBackground() {
    try {
      return loadRequestProducers();
    }
    catch (DiffRequestProducerException e) {
      return ListSelection.createSingleton(new DiffRequestProducerWrapper(new ErrorDiffRequest(e)));
    }
  }

  @RequiresEdt
  public void onAssigned(boolean isAssigned) {
    if (isAssigned) {
      if (myAssignments == 0 && myIndicator == null) {
        myIndicator = startLoading();
      }
      myAssignments++;
    }
    else {
      myAssignments--;
      if (myAssignments == 0 && myIndicator != null) {
        myIndicator.cancel();
        myIndicator = null;
      }
    }
    assert myAssignments >= 0;
  }

  @RequiresEdt
  private @Nullable ProgressIndicator startLoading() {
    if (myRequests != null) return null;

    return BackgroundTaskUtil.executeAndTryWait(indicator -> {
      ListSelection<? extends DiffRequestProducer> producers = loadRequestsInBackground();
      return () -> {
        indicator.checkCanceled();
        applyLoadedChanges(producers);
      };
    }, null);
  }

  @RequiresEdt
  private void applyLoadedChanges(@NotNull ListSelection<? extends DiffRequestProducer> producers) {
    if (myRequests != null) return;

    myRequests = producers;
    myIndicator = null;

    myDispatcher.getMulticaster().onRequestsLoaded();
  }

  @RequiresBackgroundThread
  protected abstract @NotNull ListSelection<? extends DiffRequestProducer> loadRequestProducers() throws DiffRequestProducerException;

  public interface Listener extends EventListener {
    @RequiresEdt
    void onRequestsLoaded();
  }
}
