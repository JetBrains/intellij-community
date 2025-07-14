// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl;

import com.intellij.CommonBundle;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.lang.DiffLanguage;
import com.intellij.diff.requests.*;
import com.intellij.diff.tools.util.SoftHardCacheMap;
import com.intellij.diff.util.DiffTaskQueue;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx.ScrollToPolicy;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorWithDelayedPresentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;

public abstract class CacheDiffRequestProcessor<T> extends DiffRequestProcessor {
  private static final Logger LOG = Logger.getInstance(CacheDiffRequestProcessor.class);

  private final @NotNull SoftHardCacheMap<T, DiffRequest> myRequestCache =
    new SoftHardCacheMap<>(5, 5);

  private final @NotNull DiffTaskQueue myQueue = new DiffTaskQueue();

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

  protected abstract @Nls @Nullable String getRequestName(@NotNull T provider);

  protected abstract T getCurrentRequestProvider();

  @RequiresBackgroundThread
  protected abstract @NotNull DiffRequest loadRequest(@NotNull T provider, @NotNull ProgressIndicator indicator)
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
  public void updateRequest(final boolean force, final @Nullable ScrollToPolicy scrollToChangePolicy) {
    updateRequest(force, true, scrollToChangePolicy);
  }

  @RequiresEdt
  public void updateRequest(final boolean force, boolean useCache, final @Nullable ScrollToPolicy scrollToChangePolicy) {
    ThreadingAssertions.assertEventDispatchThread();
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
        if (request instanceof ContentDiffRequest contentDiffRequest) {
          contentDiffRequest.getContents().forEach(content -> DiffLanguage.computeAndCacheLanguage(content, getProject()));
        }
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

  /**
   * NB: Method may be overridden to check if cached request is up-to-date, or needs to be updated.
   * Ex: if a reasonable `T.equals()` cannot be implemented.
   */
  protected @Nullable DiffRequest loadRequestFast(@NotNull T provider) {
    return myRequestCache.get(provider);
  }

  private @NotNull DiffRequest doLoadRequest(@NotNull T provider, @NotNull ProgressIndicator indicator) {
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
    private final @NotNull T myProducer;

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

  public abstract static class Simple extends CacheDiffRequestProcessor<DiffRequestProducer> {
    protected Simple(@Nullable Project project) {
      super(project);
    }

    protected Simple(@Nullable Project project, @NotNull String place) {
      super(project, place);
    }

    protected Simple(@Nullable Project project, @NotNull UserDataHolder context) {
      super(project, context);
    }

    @Override
    protected @Nullable String getRequestName(@NotNull DiffRequestProducer provider) {
      return provider.getName();
    }

    @Override
    protected @NotNull DiffRequest loadRequest(@NotNull DiffRequestProducer provider, @NotNull ProgressIndicator indicator)
      throws ProcessCanceledException, DiffRequestProducerException {
      return provider.process(getContext(), indicator);
    }
  }
}
