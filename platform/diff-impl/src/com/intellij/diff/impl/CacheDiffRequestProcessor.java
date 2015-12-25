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
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.Function;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.CalledInBackground;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

public abstract class CacheDiffRequestProcessor<T> extends DiffRequestProcessor {
  private static final Logger LOG = Logger.getInstance(CacheDiffRequestProcessor.class);

  @NotNull private final SoftHardCacheMap<T, DiffRequest> myRequestCache =
    new SoftHardCacheMap<T, DiffRequest>(5, 5);

  @NotNull private final DiffTaskQueue myQueue = new DiffTaskQueue();

  public CacheDiffRequestProcessor(@Nullable Project project) {
    super(project);
  }

  public CacheDiffRequestProcessor(@Nullable Project project, @NotNull String place) {
    super(project, place);
  }

  public CacheDiffRequestProcessor(@Nullable Project project,
                                   @NotNull UserDataHolder context) {
    super(project, context);
  }

  //
  // Abstract
  //

  @Nullable
  protected abstract String getRequestName(@NotNull T provider);

  protected abstract T getCurrentRequestProvider();

  @NotNull
  @CalledInBackground
  protected abstract DiffRequest loadRequest(@NotNull T provider, @NotNull ProgressIndicator indicator)
    throws ProcessCanceledException, DiffRequestProducerException;

  //
  // Update
  //

  @Override
  protected void reloadRequest() {
    updateRequest(true, false, null);
  }

  @Override
  @CalledInAwt
  public void updateRequest(final boolean force, @Nullable final ScrollToPolicy scrollToChangePolicy) {
    updateRequest(force, true, scrollToChangePolicy);
  }

  @CalledInAwt
  public void updateRequest(final boolean force, boolean useCache, @Nullable final ScrollToPolicy scrollToChangePolicy) {
    if (isDisposed()) return;

    final T requestProvider = getCurrentRequestProvider();
    if (requestProvider == null) {
      applyRequest(NoDiffRequest.INSTANCE, force, scrollToChangePolicy);
      return;
    }

    DiffRequest cachedRequest = useCache ? loadRequestFast(requestProvider) : null;
    if (cachedRequest != null) {
      applyRequest(cachedRequest, force, scrollToChangePolicy);
      return;
    }

    myQueue.executeAndTryWait(
      new Function<ProgressIndicator, Runnable>() {
        @Override
        public Runnable fun(ProgressIndicator indicator) {
          final DiffRequest request = doLoadRequest(requestProvider, indicator);
          return new Runnable() {
            @CalledInAwt
            @Override
            public void run() {
              myRequestCache.put(requestProvider, request);
              applyRequest(request, force, scrollToChangePolicy);
            }
          };
        }
      },
      new Runnable() {
        @Override
        public void run() {
          applyRequest(new LoadingDiffRequest(getRequestName(requestProvider)), force, scrollToChangePolicy);
        }
      },
      ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS
    );
  }

  @Nullable
  protected DiffRequest loadRequestFast(@NotNull T provider) {
    return myRequestCache.get(provider);
  }

  @NotNull
  private DiffRequest doLoadRequest(@NotNull T provider, @NotNull ProgressIndicator indicator) {
    String name = getRequestName(provider);
    try {
      return loadRequest(provider, indicator);
    }
    catch (ProcessCanceledException e) {
      OperationCanceledDiffRequest request = new OperationCanceledDiffRequest(name);
      request.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, Collections.<AnAction>singletonList(new ReloadRequestAction(provider)));
      return request;
    }
    catch (DiffRequestProducerException e) {
      return new ErrorDiffRequest(name, e);
    }
    catch (Exception e) {
      LOG.warn(e);
      return new ErrorDiffRequest(name, e);
    }
  }

  @Override
  @CalledInAwt
  protected void onDispose() {
    super.onDispose();
    myQueue.abort();
    myRequestCache.clear();
  }

  //
  // Actions
  //

  protected class ReloadRequestAction extends DumbAwareAction {
    @NotNull private final T myProducer;

    public ReloadRequestAction(@NotNull T provider) {
      super("Reload", null, AllIcons.Actions.Refresh);
      myProducer = provider;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      myRequestCache.remove(myProducer);
      updateRequest(true);
    }
  }
}
