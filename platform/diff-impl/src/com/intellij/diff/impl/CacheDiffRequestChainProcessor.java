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
import com.intellij.diff.requests.*;
import com.intellij.diff.tools.util.SoftHardCacheMap;
import com.intellij.diff.util.DiffTaskQueue;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class CacheDiffRequestChainProcessor extends DiffRequestProcessor {
  private static final Logger LOG = Logger.getInstance(CacheDiffRequestChainProcessor.class);

  @NotNull private final DiffRequestChain myRequestChain;

  @NotNull private final SoftHardCacheMap<DiffRequestProducer, DiffRequest> myRequestCache =
    new SoftHardCacheMap<DiffRequestProducer, DiffRequest>(5, 5);

  @NotNull private final DiffTaskQueue myQueue = new DiffTaskQueue();

  public CacheDiffRequestChainProcessor(@Nullable Project project, @NotNull DiffRequestChain requestChain) {
    super(project, requestChain);
    myRequestChain = requestChain;
  }

  //
  // Update
  //

  @CalledInAwt
  public void updateRequest(final boolean force, @Nullable final ScrollToPolicy scrollToChangePolicy) {
    List<? extends DiffRequestProducer> requests = myRequestChain.getRequests();
    int index = myRequestChain.getIndex();
    if (index < 0 || index >= requests.size()) {
      applyRequest(NoDiffRequest.INSTANCE, force, scrollToChangePolicy);
      return;
    }

    final DiffRequestProducer producer = requests.get(index);

    DiffRequest request = loadRequestFast(producer);
    if (request != null) {
      applyRequest(request, force, scrollToChangePolicy);
      return;
    }

    myQueue.executeAndTryWait(
      new Function<ProgressIndicator, Runnable>() {
        @Override
        public Runnable fun(ProgressIndicator indicator) {
          final DiffRequest request = loadRequest(producer, indicator);
          return new Runnable() {
            @CalledInAwt
            @Override
            public void run() {
              myRequestCache.put(producer, request);
              applyRequest(request, force, scrollToChangePolicy);
            }
          };
        }
      },
      new Runnable() {
        @Override
        public void run() {
          applyRequest(new LoadingDiffRequest(producer.getName()), force, scrollToChangePolicy);
        }
      },
      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
    );
  }

  @Nullable
  protected DiffRequest loadRequestFast(@NotNull DiffRequestProducer producer) {
    return myRequestCache.get(producer);
  }

  @NotNull
  @CalledInBackground
  private DiffRequest loadRequest(@NotNull DiffRequestProducer producer, @NotNull ProgressIndicator indicator) {
    try {
      return producer.process(getContext(), indicator);
    }
    catch (ProcessCanceledException e) {
      OperationCanceledDiffRequest request = new OperationCanceledDiffRequest(producer.getName());
      request.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, Collections.<AnAction>singletonList(new ReloadRequestAction(producer)));
      return request;
    }
    catch (DiffRequestProducerException e) {
      return new ErrorDiffRequest(producer, e);
    }
    catch (Exception e) {
      return new ErrorDiffRequest(producer, e);
    }
  }

  //
  // Misc
  //

  @Override
  @CalledInAwt
  protected void onDispose() {
    super.onDispose();
    myQueue.abort();
    myRequestCache.clear();
  }

  @NotNull
  @Override
  protected List<AnAction> getNavigationActions() {
    return ContainerUtil.list(
      new MyPrevDifferenceAction(),
      new MyNextDifferenceAction(),
      new MyPrevChangeAction(),
      new MyNextChangeAction(),
      createGoToChangeAction()
    );
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
    return GoToChangePopupBuilder.create(myRequestChain, new Consumer<Integer>() {
      @Override
      public void consume(Integer index) {
        if (index >= 0 && index != myRequestChain.getIndex()) {
          myRequestChain.setIndex(index);
          updateRequest();
        }
      }
    });
  }

  //
  // Actions
  //

  protected class ReloadRequestAction extends DumbAwareAction {
    @NotNull private final DiffRequestProducer myProducer;

    public ReloadRequestAction(@NotNull DiffRequestProducer producer) {
      super("Reload", null, AllIcons.Actions.Refresh);
      myProducer = producer;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myRequestCache.remove(myProducer);
      updateRequest(true);
    }
  }
}
