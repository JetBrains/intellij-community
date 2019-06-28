// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.AsyncDiffRequestChain;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class CacheDiffRequestChainProcessor extends CacheDiffRequestProcessor<DiffRequestProducer> {
  @NotNull private final DiffRequestChain myRequestChain;
  private int myIndex;

  public CacheDiffRequestChainProcessor(@Nullable Project project, @NotNull DiffRequestChain requestChain) {
    super(project, requestChain);
    myRequestChain = requestChain;

    if (myRequestChain instanceof AsyncDiffRequestChain) {
      ((AsyncDiffRequestChain)myRequestChain).onAssigned(true);
      // listener should be added after `onAssigned` call to avoid notification about synchronously loaded requests
      ((AsyncDiffRequestChain)myRequestChain).addListener(new MyChangeListener(), this);
    }

    myIndex = myRequestChain.getIndex();
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
    if (myIndex < 0 || myIndex >= requests.size()) return null;
    return requests.get(myIndex);
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
    return myIndex < myRequestChain.getRequests().size() - 1;
  }

  @Override
  protected boolean hasPrevChange() {
    return myIndex > 0;
  }

  @Override
  protected void goToNextChange(boolean fromDifferences) {
    myIndex += 1;
    updateRequest(false, fromDifferences ? ScrollToPolicy.FIRST_CHANGE : null);
  }

  @Override
  protected void goToPrevChange(boolean fromDifferences) {
    myIndex -= 1;
    updateRequest(false, fromDifferences ? ScrollToPolicy.LAST_CHANGE : null);
  }

  @Override
  protected boolean isNavigationEnabled() {
    return myRequestChain.getRequests().size() > 1;
  }

  @NotNull
  private AnAction createGoToChangeAction() {
    AnAction action = GoToChangePopupBuilder.create(myRequestChain, index -> {
      if (index >= 0 && index < myRequestChain.getRequests().size() && index != myIndex) {
        myIndex = index;
        updateRequest();
      }
    }, myIndex);
    if (DiffUtil.isUserDataFlagSet(DiffUserDataKeysEx.DIFF_IN_EDITOR, getContext())) {
      patchShortcutSet(action, "GotoClass", null);
    }
    return action;
  }

  private class MyChangeListener implements AsyncDiffRequestChain.Listener {
    @Override
    public void onRequestsLoaded() {
      dropCaches();
      myIndex = myRequestChain.getIndex();
      updateRequest(true);
    }
  }
}
