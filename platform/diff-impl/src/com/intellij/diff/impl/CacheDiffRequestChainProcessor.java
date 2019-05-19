// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.AsyncDiffRequestChain;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class CacheDiffRequestChainProcessor extends CacheDiffRequestProcessor<DiffRequestProducer> {
  private static final Logger LOG = Logger.getInstance(CacheDiffRequestChainProcessor.class);

  @NotNull private final DiffRequestChain myRequestChain;

  public CacheDiffRequestChainProcessor(@Nullable Project project, @NotNull DiffRequestChain requestChain) {
    super(project, requestChain);
    myRequestChain = requestChain;

    if (myRequestChain instanceof AsyncDiffRequestChain) {
      ((AsyncDiffRequestChain)myRequestChain).onAssigned(true);
      ((AsyncDiffRequestChain)myRequestChain).addListener(new MyChangeListener(), this);
    }
  }

  @Override
  protected void onDispose() {
    if (myRequestChain instanceof AsyncDiffRequestChain) {
      ((AsyncDiffRequestChain)myRequestChain).onAssigned(false);
    }

    super.onDispose();
  }

  //
  // Update
  //


  @NotNull
  @Override
  protected String getRequestName(@NotNull DiffRequestProducer producer) {
    return producer.getName();
  }

  @Override
  protected DiffRequestProducer getCurrentRequestProvider() {
    List<? extends DiffRequestProducer> requests = myRequestChain.getRequests();
    int index = myRequestChain.getIndex();
    if (index < 0 || index >= requests.size()) return null;
    return requests.get(index);
  }

  @NotNull
  @Override
  protected DiffRequest loadRequest(@NotNull DiffRequestProducer producer, @NotNull ProgressIndicator indicator)
    throws ProcessCanceledException, DiffRequestProducerException {
    return producer.process(getContext(), indicator);
  }

  //
  // Getters
  //

  @NotNull
  public DiffRequestChain getRequestChain() {
    return myRequestChain;
  }

  //
  // Navigation
  //

  @NotNull
  @Override
  protected List<AnAction> getNavigationActions() {
    return Arrays.asList(new MyPrevDifferenceAction(), new MyNextDifferenceAction(), new MyOpenInEditorAction(), Separator.getInstance(),
                         new MyPrevChangeAction(), new MyNextChangeAction(), createGoToChangeAction());
  }

  @Override
  protected boolean hasNextChange() {
    return myRequestChain.getIndex() < myRequestChain.getRequests().size() - 1;
  }

  @Override
  protected boolean hasPrevChange() {
    return myRequestChain.getIndex() > 0;
  }

  @Override
  protected void goToNextChange(boolean fromDifferences) {
    myRequestChain.setIndex(myRequestChain.getIndex() + 1);
    updateRequest(false, fromDifferences ? ScrollToPolicy.FIRST_CHANGE : null);
  }

  @Override
  protected void goToPrevChange(boolean fromDifferences) {
    myRequestChain.setIndex(myRequestChain.getIndex() - 1);
    updateRequest(false, fromDifferences ? ScrollToPolicy.LAST_CHANGE : null);
  }

  @Override
  protected boolean isNavigationEnabled() {
    return myRequestChain.getRequests().size() > 1;
  }

  @NotNull
  private AnAction createGoToChangeAction() {
    return GoToChangePopupBuilder.create(myRequestChain, index -> {
      if (index >= 0 && index != myRequestChain.getIndex()) {
        myRequestChain.setIndex(index);
        updateRequest();
      }
    });
  }

  private class MyChangeListener implements AsyncDiffRequestChain.Listener {
    @Override
    public void onRequestsLoaded() {
      dropCaches();
      updateRequest(true);
    }
  }
}
