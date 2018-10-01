// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Implementation of {@link MultithreadSearcher.Listener} which decrease events rate and raise batch updates
 * each {@code throttlingDelay} milliseconds.
 * <br>
 * Not thread-safe. So could be notified in single thread only
 */
class ThrottlingListenerWrapper implements MultithreadSearcher.Listener {

  public final int myThrottlingDelay;

  private final MultithreadSearcher.Listener myDelegateListener;
  private final Executor myDelegateExecutor;

  private final Buffer myBuffer = new Buffer();
  private final BiConsumer<List<SESearcher.ElementInfo>, List<SESearcher.ElementInfo>> myFlushConsumer;

  ThrottlingListenerWrapper(int throttlingDelay, MultithreadSearcher.Listener delegateListener, Executor delegateExecutor) {
    myThrottlingDelay = throttlingDelay;
    myDelegateListener = delegateListener;
    myDelegateExecutor = delegateExecutor;

    myFlushConsumer = (added, removed) -> {
      if (!added.isEmpty()) {
        myDelegateExecutor.execute(() -> myDelegateListener.elementsAdded(added));
      }
      if (!removed.isEmpty()) {
        myDelegateExecutor.execute(() -> myDelegateListener.elementsRemoved(removed));
      }
    };
  }

  public void clearBuffer() {
    myBuffer.clear();
  }

  @Override
  public void elementsAdded(@NotNull List<SESearcher.ElementInfo> list) {
    myBuffer.addEvent(new Event(Event.ADD, list));
    flushBufferIfNeeded();
  }

  @Override
  public void elementsRemoved(@NotNull List<SESearcher.ElementInfo> list) {
    myBuffer.addEvent(new Event(Event.REMOVE, list));
    flushBufferIfNeeded();
  }

  @Override
  public void searchFinished(@NotNull Map<SearchEverywhereContributor<?>, Boolean> hasMoreContributors) {
    myBuffer.flush(myFlushConsumer);
    myDelegateExecutor.execute(() -> myDelegateListener.searchFinished(hasMoreContributors));
  }

  private void flushBufferIfNeeded() {
    myBuffer.getOldestEventTime().ifPresent(time -> {
      if (System.currentTimeMillis() - time > myThrottlingDelay) {
        myBuffer.flush(myFlushConsumer);
      }
    });
  }

  private static class Event {
    static final int REMOVE = 0;
    static final int ADD = 1;

    final int type;
    final List<SESearcher.ElementInfo> items;

    Event(int type, List<SESearcher.ElementInfo> items) {
      this.type = type;
      this.items = items;
    }
  }

  private static class Buffer {
    private final Queue<Pair<Event, Long>> myQueue = new ArrayDeque<>();

    public void addEvent(Event event) {
      myQueue.add(Pair.create(event, System.currentTimeMillis()));
    }

    public Optional<Long> getOldestEventTime() {
      return Optional.ofNullable(myQueue.peek()).map(pair -> pair.second);
    }

    public void flush(BiConsumer<List<SESearcher.ElementInfo>, List<SESearcher.ElementInfo>> consumer) {
      List<SESearcher.ElementInfo> added = new ArrayList<>();
      List<SESearcher.ElementInfo> removed = new ArrayList<>();
      myQueue.forEach(pair -> {
        Event event = pair.first;
        if (event.type == Event.ADD) {
          added.addAll(event.items);
        } else {
          event.items.forEach(removing -> {
            if (!added.removeIf(existing -> existing.getContributor() == removing.getContributor() && existing.getElement() == removing.getElement())) {
              removed.add(removing);
            }
          });
        }
      });
      myQueue.clear();
      consumer.accept(added, removed);
    }

    public void clear() {
      myQueue.clear();
    }
  }


}
