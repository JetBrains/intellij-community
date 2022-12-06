// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.options.advanced.AdvancedSettings;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;

class SwitchSEListener extends BufferingListenerWrapper {

  private final ThrottlingListenerWrapper throttlingListener;
  private final WaitForContributorsListenerWrapper wfcListener;
  private boolean useWFC = false;

  SwitchSEListener(SearchListener delegateListener, SearchListModel model) {
    super(delegateListener);
    throttlingListener = new ThrottlingListenerWrapper(delegateListener);
    wfcListener = new WaitForContributorsListenerWrapper(delegateListener, model, AdvancedSettings.getInt("search.everywhere.contributors.wait.timeout"));
  }

  @Override
  public void searchStarted(@NotNull Collection<? extends SearchEverywhereContributor<?>> contributors) {
    useWFC = contributors.size() > 1;
    getListener().searchStarted(contributors);
  }

  @Override
  public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
    getListener().searchFinished(hasMoreContributors);
  }

  @Override
  public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    getListener().elementsAdded(list);
  }

  @Override
  public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    getListener().elementsRemoved(list);
  }

  @Override
  public void contributorWaits(@NotNull SearchEverywhereContributor<?> contributor) {
    getListener().contributorWaits(contributor);
  }

  @Override
  public void contributorFinished(@NotNull SearchEverywhereContributor<?> contributor, boolean hasMore) {
    getListener().contributorFinished(contributor, hasMore);
  }

  @Override
  public void clearBuffer() {
    getListener().clearBuffer();
  }

  @Override
  protected void flushBuffer() {
    getListener().flushBuffer();
  }

  private BufferingListenerWrapper getListener() {
    return useWFC ? wfcListener : throttlingListener;
  }
}
