// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.AsyncDiffRequestChain;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.diff.util.DiffUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class CacheDiffRequestChainProcessor extends CacheDiffRequestProcessor.Simple {
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

  @Override
  protected DiffRequestProducer getCurrentRequestProvider() {
    List<? extends DiffRequestProducer> requests = myRequestChain.getRequests();
    if (myIndex < 0 || myIndex >= requests.size()) return null;
    return requests.get(myIndex);
  }

  //
  // Getters
  //

  @NotNull
  public DiffRequestChain getRequestChain() {
    return myRequestChain;
  }

  public void setCurrentRequest(int index) {
    myIndex = index;
    updateRequest();
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
  protected boolean hasNextChange(boolean fromUpdate) {
    return myIndex < myRequestChain.getRequests().size() - 1;
  }

  @Override
  protected boolean hasPrevChange(boolean fromUpdate) {
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
        setCurrentRequest(index);
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
