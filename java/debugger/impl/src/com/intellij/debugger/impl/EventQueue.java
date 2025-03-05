// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.PossiblySyncCommand;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class EventQueue<E> {
  private static final Logger LOG = Logger.getInstance(EventQueue.class);

  private final LinkedList[] myEvents;
  private final ReentrantLock myLock;
  private final Condition myEventsAvailable;

  private volatile E myCurrentEvent;

  private volatile boolean myIsClosed = false;

  public EventQueue(int countPriorities) {
    myLock = new ReentrantLock();
    myEventsAvailable = myLock.newCondition();
    myEvents = new LinkedList[countPriorities];
    for (int i = 0; i < myEvents.length; i++) {
      myEvents[i] = new LinkedList<E>();
    }
  }

  public boolean pushBack(@NotNull E event, int priority) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("pushBack event " + event);
    }

    myLock.lock();
    try {
      if (isClosed()) {
        return false;
      }
      getEventsList(priority).addFirst(event);
      myEventsAvailable.signalAll();
    }
    finally {
      myLock.unlock();
    }
    return true;
  }

  public boolean put(@NotNull E event, int priority) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("put event " + event);
    }

    myLock.lock();
    try {
      if (isClosed()) {
        return false;
      }
      getEventsList(priority).offer(event);
      myEventsAvailable.signalAll();
    }
    finally {
      myLock.unlock();
    }
    return true;
  }

  private LinkedList<E> getEventsList(final int priority) {
    return (LinkedList<E>)myEvents[priority];
  }

  public void close() {
    myLock.lock();
    try {
      myIsClosed = true;
      myEventsAvailable.signalAll();
    }
    finally {
      myLock.unlock();
    }
  }

  private E getEvent() throws EventQueueClosedException {
    myLock.lock();
    try {
      while (true) {
        if (myIsClosed) {
          throw new EventQueueClosedException();
        }
        for (int i = 0; i < myEvents.length; i++) {
          final E event = getEventsList(i).poll();
          if (event != null) {
            return event;
          }
        }
        myEventsAvailable.awaitUninterruptibly();
      }
    }
    finally {
      myLock.unlock();
    }
  }

  public E get() throws EventQueueClosedException {
    try {
      return myCurrentEvent = getEvent();
    }
    catch (EventQueueClosedException e) {
      myCurrentEvent = null; // cleanup
      throw e;
    }
  }

  public boolean isClosed() {
    return myIsClosed;
  }

  public E getCurrentEvent() {
    return myCurrentEvent;
  }

  public @NotNull List<E> clearQueue() {
    final List<E> allEvents = new ArrayList<>();
    for (int i = 0; i < myEvents.length; i++) {
      final LinkedList<E> eventList = getEventsList(i);
      for (E event = eventList.poll(); event != null; event = eventList.poll()) {
        allEvents.add(event);
      }
    }
    return allEvents;
  }

  public boolean isEmpty() {
    return ConcurrencyUtil.withLock(myLock, () -> ContainerUtil.and(myEvents, AbstractCollection::isEmpty));
  }

  public boolean hasAsyncCommands() {
    return ConcurrencyUtil.withLock(myLock, () ->
      Arrays.stream(myEvents).flatMap(Collection::stream).anyMatch(c -> !(c instanceof PossiblySyncCommand)));
  }

  public void reopen() {
    myIsClosed = false;
  }
}
