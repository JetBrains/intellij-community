// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Not thread-safe and should be notified only in EDT
 */
public abstract class BufferingListenerWrapper implements SearchListener {

  protected final Buffer myBuffer = new Buffer();
  protected final SearchListener myDelegateListener;

  public BufferingListenerWrapper(SearchListener delegateListener) { myDelegateListener = delegateListener; }

  public void clearBuffer() {
    myBuffer.clear();
    if (myDelegateListener instanceof BufferingListenerWrapper) {
      ((BufferingListenerWrapper)myDelegateListener).clearBuffer();
    }
  }

  protected void flushBuffer() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myBuffer.flush();
  }

  @Override
  public void elementsAdded(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    myBuffer.addEvent(new Event(Event.ADD, list));
  }

  @Override
  public void elementsRemoved(@NotNull List<? extends SearchEverywhereFoundElementInfo> list) {
    myBuffer.addEvent(new Event(Event.REMOVE, list));
  }

  @Override
  public void searchStarted(@NotNull Collection<? extends SearchEverywhereContributor<?>> contributors) {
    myDelegateListener.searchStarted(contributors);
  }

  @Override
  public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
    myBuffer.flush();
    myDelegateListener.searchFinished(hasMoreContributors);
  }

  @Override
  public void contributorWaits(@NotNull SearchEverywhereContributor<?> contributor) {
    myDelegateListener.contributorWaits(contributor);
  }

  protected static class Event {
    static final int REMOVE = 0;
    static final int ADD = 1;

    final int type;
    final List<? extends SearchEverywhereFoundElementInfo> items;

    Event(int type, List<? extends SearchEverywhereFoundElementInfo> items) {
      this.type = type;
      this.items = items;
    }
  }

  private class Buffer {
    private final Queue<Event> myQueue = new ArrayDeque<>();

    public void addEvent(Event event) {
      myQueue.add(event);
    }

    public void flush() {
      List<SearchEverywhereFoundElementInfo> added = new ArrayList<>();
      List<SearchEverywhereFoundElementInfo> removed = new ArrayList<>();
      myQueue.forEach(event -> {
        if (event.type == Event.ADD) {
          added.addAll(event.items);
        }
        else {
          event.items.forEach(removing -> {
            if (!added.removeIf(existing -> areTheSame(removing, existing))) {
              removed.add(removing);
            }
          });
        }
      });
      myQueue.clear();

      if (!added.isEmpty()) myDelegateListener.elementsAdded(added);
      if (!removed.isEmpty()) myDelegateListener.elementsRemoved(removed);
    }

    private boolean areTheSame(SearchEverywhereFoundElementInfo removing, SearchEverywhereFoundElementInfo existing) {
      return existing.getContributor() == removing.getContributor() && existing.getElement() == removing.getElement();
    }

    public void clear() {
      myQueue.clear();
    }
  }
}
