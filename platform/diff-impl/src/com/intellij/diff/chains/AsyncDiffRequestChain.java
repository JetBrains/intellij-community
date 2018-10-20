// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.chains;

import com.intellij.diff.chains.SimpleDiffRequestChain.DiffRequestProducerWrapper;
import com.intellij.diff.requests.ErrorDiffRequest;
import com.intellij.diff.requests.LoadingDiffRequest;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.diagnostic.Logger.getInstance;

public abstract class AsyncDiffRequestChain extends DiffRequestChainBase implements MutableDiffRequestChain {
  private static final Logger LOG = getInstance(AsyncDiffRequestChain.class);

  private final EventDispatcher<Listener> myDispatcher = EventDispatcher.create(Listener.class);

  private List<? extends DiffRequestProducer> myRequests = null;

  @Nullable private ProgressIndicator myIndicator;
  private int myAssignments = 0;

  @Override
  public void addListener(@NotNull Listener listener, @NotNull Disposable disposable) {
    myDispatcher.addListener(listener, disposable);
  }

  @NotNull
  @Override
  public List<? extends DiffRequestProducer> getRequests() {
    List<? extends DiffRequestProducer> requests = myRequests;
    if (requests == null) {
      return Collections.singletonList(new DiffRequestProducerWrapper(new LoadingDiffRequest()));
    }
    return requests;
  }

  @Override
  @CalledInAwt
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

  @Nullable
  @CalledInAwt
  private ProgressIndicator startLoading() {
    if (myRequests != null) return null;

    return BackgroundTaskUtil.executeAndTryWait(indicator -> {
      ListSelection<? extends DiffRequestProducer> producers;
      try {
        producers = loadRequestProducers();
      }
      catch (DiffRequestProducerException e) {
        producers = ListSelection.createSingleton(new DiffRequestProducerWrapper(new ErrorDiffRequest(e)));
      }

      ListSelection<? extends DiffRequestProducer> finalProducers = producers;
      return () -> {
        indicator.checkCanceled();

        if (myRequests != null) {
          LOG.error("Changes are loaded twice");
          return;
        }

        myRequests = finalProducers.getList();
        setIndex(finalProducers.getSelectedIndex());
        myIndicator = null;

        myDispatcher.getMulticaster().onChainChange();
      };
    }, null);
  }

  @NotNull
  @CalledInBackground
  protected abstract ListSelection<? extends DiffRequestProducer> loadRequestProducers() throws DiffRequestProducerException;
}
