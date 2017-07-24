/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.impl;

import com.intellij.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class CacheDiffRequestChainProcessor extends CacheDiffRequestProcessor<DiffRequestProducer> {
  private static final Logger LOG = Logger.getInstance(CacheDiffRequestChainProcessor.class);

  @NotNull private final DiffRequestChain myRequestChain;

  public CacheDiffRequestChainProcessor(@Nullable Project project, @NotNull DiffRequestChain requestChain) {
    super(project, requestChain);
    myRequestChain = requestChain;
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
    return ContainerUtil.list(
      ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_DIFF),
      ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_DIFF),
      new MyPrevChangeAction(),
      new MyNextChangeAction(),
      createGoToChangeAction()
    );
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
}
