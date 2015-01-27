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
package com.intellij.openapi.util.diff.impl;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.requests.*;
import com.intellij.openapi.util.diff.tools.util.SoftHardCacheMap;
import com.intellij.openapi.util.diff.util.CalledInBackground;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys.ScrollToPolicy;
import com.intellij.openapi.util.diff.util.WaitingBackgroundableTaskExecutor;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class CacheDiffRequestChainProcessor extends DiffRequestProcessor {
  private static final Logger LOG = Logger.getInstance(CacheDiffRequestChainProcessor.class);

  @NotNull private final DiffRequestChain myRequestChain;

  @NotNull private final SoftHardCacheMap<DiffRequestPresentable, DiffRequest> myRequestCache =
    new SoftHardCacheMap<DiffRequestPresentable, DiffRequest>(5, 5);

  @NotNull private final WaitingBackgroundableTaskExecutor myTaskExecutor = new WaitingBackgroundableTaskExecutor();

  public CacheDiffRequestChainProcessor(@Nullable Project project, @NotNull DiffRequestChain requestChain) {
    super(project);
    myRequestChain = requestChain;
  }

  @Override
  public void init() {
    super.init();

    if (myRequestChain.getRequests().isEmpty()) {
      applyRequest(new NoDiffRequest(), true, null);
    }
    else {
      applyRequest(new LoadingDiffRequest(), true, null);
    }
  }

  //
  // Update
  //

  public void updateRequest(final boolean force, @Nullable final ScrollToPolicy scrollToChangePolicy) {
    List<? extends DiffRequestPresentable> requests = myRequestChain.getRequests();
    int index = myRequestChain.getIndex();
    if (index < 0 || index >= requests.size()) {
      applyRequest(new NoDiffRequest(), force, scrollToChangePolicy);
      return;
    }

    final DiffRequestPresentable presentable = requests.get(index);

    DiffRequest request = loadRequestFast(presentable);
    if (request != null) {
      applyRequest(request, force, scrollToChangePolicy);
      return;
    }

    myTaskExecutor.execute(
      new Convertor<ProgressIndicator, Runnable>() {
        @Override
        public Runnable convert(ProgressIndicator indicator) {
          final DiffRequest request = loadRequest(presentable, indicator);
          return new Runnable() {
            @Override
            public void run() {
              myRequestCache.put(presentable, request);
              applyRequest(request, force, scrollToChangePolicy);
            }
          };
        }
      },
      new Runnable() {
        @Override
        public void run() {
          applyRequest(new LoadingDiffRequest(presentable.getName()), force, scrollToChangePolicy);
        }
      },
      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
    );
  }

  @Nullable
  protected DiffRequest loadRequestFast(@NotNull DiffRequestPresentable presentable) {
    return myRequestCache.get(presentable);
  }

  @NotNull
  @CalledInBackground
  private DiffRequest loadRequest(@NotNull DiffRequestPresentable presentable, @NotNull ProgressIndicator indicator) {
    try {
      return presentable.process(getContext(), indicator);
    }
    catch (ProcessCanceledException e) {
      OperationCanceledDiffRequest request = new OperationCanceledDiffRequest(presentable.getName());
      request.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, Collections.<AnAction>singletonList(new ReloadRequestAction(presentable)));
      return request;
    }
    catch (DiffRequestPresentableException e) {
      return new ErrorDiffRequest(presentable, e);
    }
    catch (Exception e) {
      return new ErrorDiffRequest(presentable, e);
    }
  }

  //
  // Abstract
  //

  protected void setWindowTitle(@NotNull String title) {
  }

  protected void onAfterNavigate() {
  }

  //
  // Misc
  //

  @Override
  protected void onDispose() {
    super.onDispose();
    myTaskExecutor.abort();
    myRequestCache.clear();
  }

  @Nullable
  @Override
  public <T> T getContextUserData(@NotNull Key<T> key) {
    return myRequestChain.getUserData(key);
  }

  @Override
  public <T> void putContextUserData(@NotNull Key<T> key, @Nullable T value) {
    myRequestChain.putUserData(key, value);
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
    @NotNull private final DiffRequestPresentable myPresentable;

    public ReloadRequestAction(@NotNull DiffRequestPresentable presentable) {
      super("Reload", null, AllIcons.Actions.Refresh);
      myPresentable = presentable;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myRequestCache.remove(myPresentable);
      updateRequest(true);
    }
  }
}
