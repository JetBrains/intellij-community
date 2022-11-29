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

import com.intellij.CommonBundle;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.*;
import com.intellij.diff.tools.util.SoftHardCacheMap;
import com.intellij.diff.util.DiffTaskQueue;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;

public abstract class CacheDiffRequestProcessor<T> extends DiffRequestProcessor {
  private static final Logger LOG = Logger.getInstance(CacheDiffRequestProcessor.class);

  @NotNull private final SoftHardCacheMap<T, DiffRequest> myRequestCache =
    new SoftHardCacheMap<>(5, 5);

  @NotNull private final DiffTaskQueue myQueue = new DiffTaskQueue();

  private @Nullable T myQueuedProvider = null;
  private boolean myValidateQueuedProvider = false;

  public CacheDiffRequestProcessor(@Nullable Project project) {
    super(project);
  }

  public CacheDiffRequestProcessor(@Nullable Project project, @NotNull String place) {
    super(project, place);
  }

  public CacheDiffRequestProcessor(@Nullable Project project, @NotNull UserDataHolder context) {
    super(project, context);
  }

  //
  // Abstract
  //

  @Nls
  @Nullable
  protected abstract String getRequestName(@NotNull T provider);

  protected abstract T getCurrentRequestProvider();

  @NotNull
  @RequiresBackgroundThread
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
  @RequiresEdt
  public void updateRequest(final boolean force, @Nullable final ScrollToPolicy scrollToChangePolicy) {
    updateRequest(force, true, scrollToChangePolicy);
  }

  @RequiresEdt
  public void updateRequest(final boolean force, boolean useCache, @Nullable final ScrollToPolicy scrollToChangePolicy) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (isDisposed()) return;

    final T requestProvider = getCurrentRequestProvider();
    if (requestProvider == null) {
      myQueue.abort();
      finishUpdate(NoDiffRequest.INSTANCE, force, scrollToChangePolicy);
      return;
    }

    DiffRequest cachedRequest = useCache ? loadRequestFast(requestProvider) : null;
    if (cachedRequest != null) {
      myQueue.abort();
      finishUpdate(cachedRequest, force, scrollToChangePolicy);
      return;
    }

    if (useCache && !force && Objects.equals(myQueuedProvider, requestProvider)) {
      // Let the ongoing computation to finish - high chance, it will be up-to-date with our request.
      // Schedule double-check in case 'loadRequestFast' implements additional validation.
      myValidateQueuedProvider = true;
      return;
    }

    myQueuedProvider = requestProvider;
    myValidateQueuedProvider = false;

    myQueue.executeAndTryWait(
      indicator -> {
        final DiffRequest request = doLoadRequest(requestProvider, indicator);
        return () -> finishRequestLoading(request, force, scrollToChangePolicy, requestProvider);
      },
      () -> applyRequest(new LoadingDiffRequest(getRequestName(requestProvider)), force, scrollToChangePolicy),
      getFastLoadingTimeMillis()
    );
  }

  @RequiresEdt
  private void finishRequestLoading(@NotNull DiffRequest request,
                                    boolean force,
                                    @Nullable ScrollToPolicy scrollToChangePolicy,
                                    @NotNull T requestProvider) {
    boolean shouldValidate = myValidateQueuedProvider;

    myRequestCache.put(requestProvider, request);
    finishUpdate(request, force, scrollToChangePolicy);

    if (shouldValidate) {
      updateRequest();
    }
  }

  @RequiresEdt
  private void finishUpdate(@NotNull DiffRequest request, boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
    myQueuedProvider = null;
    myValidateQueuedProvider = false;
    applyRequest(request, force, scrollToChangePolicy);
  }

  protected int getFastLoadingTimeMillis() {
    return ProgressIndicatorWithDelayedPresentation.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;
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
      request.putUserData(DiffUserDataKeys.CONTEXT_ACTIONS, Collections.singletonList(new ReloadRequestAction(provider)));
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
  @RequiresEdt
  protected void onDispose() {
    super.onDispose();
    myQueue.abort();
    myRequestCache.clear();
  }

  protected void dropCaches() {
    myRequestCache.clear();
  }

  //
  // Actions
  //

  protected class ReloadRequestAction extends DumbAwareAction {
    @NotNull private final T myProducer;

    public ReloadRequestAction(@NotNull T provider) {
      super(CommonBundle.message("action.text.reload"), null, AllIcons.Actions.Refresh);
      myProducer = provider;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myRequestCache.remove(myProducer);
      updateRequest(true);
    }
  }

  public static abstract class Simple extends CacheDiffRequestProcessor<DiffRequestProducer> {
    protected Simple(@Nullable Project project) {
      super(project);
    }

    protected Simple(@Nullable Project project, @NotNull String place) {
      super(project, place);
    }

    protected Simple(@Nullable Project project, @NotNull UserDataHolder context) {
      super(project, context);
    }

    @Nullable
    @Override
    protected String getRequestName(@NotNull DiffRequestProducer provider) {
      return provider.getName();
    }

    @NotNull
    @Override
    protected DiffRequest loadRequest(@NotNull DiffRequestProducer provider, @NotNull ProgressIndicator indicator)
      throws ProcessCanceledException, DiffRequestProducerException {
      return provider.process(getContext(), indicator);
    }
  }
}
