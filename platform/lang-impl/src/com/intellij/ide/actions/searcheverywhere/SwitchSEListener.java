// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

class SwitchSEListener extends BufferingListenerWrapper {

  private final ThrottlingListenerWrapper throttlingListener;
  private final WaitForContributorsListenerWrapper wfcListener;
  private boolean useWFC = false;

  public SwitchSEListener(SearchListener delegateListener, SearchListModel model) {
    super(delegateListener);
    throttlingListener = new ThrottlingListenerWrapper(delegateListener);
    wfcListener = new WaitForContributorsListenerWrapper(delegateListener, model);
  }

  @Override
  public void searchStarted(@NotNull Collection<? extends SearchEverywhereContributor<?>> contributors) {
    useWFC = contributors.size() > 1;
    chooseListenerAndPerform(l -> l.searchStarted(contributors));
  }

  @Override
  public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
    chooseListenerAndPerform(l -> l.searchFinished(hasMoreContributors));
  }

  @Override
  public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    chooseListenerAndPerform(l -> l.elementsAdded(list));
  }

  @Override
  public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    chooseListenerAndPerform(l -> l.elementsRemoved(list));
  }

  @Override
  public void contributorWaits(@NotNull SearchEverywhereContributor<?> contributor) {
    chooseListenerAndPerform(l -> l.contributorWaits(contributor));
  }

  @Override
  public void contributorFinished(@NotNull SearchEverywhereContributor<?> contributor, boolean hasMore) {
    chooseListenerAndPerform(l -> l.contributorFinished(contributor, hasMore));
  }

  @Override
  public void clearBuffer() {
    chooseListenerAndPerform(l -> l.clearBuffer());
  }

  @Override
  protected void flushBuffer() {
    chooseListenerAndPerform(l -> l.flushBuffer());
  }

  private void chooseListenerAndPerform(Consumer<BufferingListenerWrapper> command) {
    BufferingListenerWrapper listener = useWFC ? wfcListener : throttlingListener;
    command.accept(listener);
  }
}
