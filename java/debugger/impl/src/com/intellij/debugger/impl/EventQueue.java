// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.debugger.engine.PossiblySyncCommand;
import com.intellij.openapi.diagnostic.Logger;
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

  private int myAsyncCommandsCounter = 0;

  public EventQueue(int countPriorities) {
    myLock = new ReentrantLock();
    myEventsAvailable = myLock.newCondition();
    myEvents = new LinkedList[countPriorities];
    Arrays.setAll(myEvents, i -> new LinkedList<E>());
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
      processAdd(event);
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
      processAdd(event);
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
          E event = getEventsList(i).poll();
          if (event != null) {
            processRemove(event);
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
    myLock.lock();
    try {
      List<E> allEvents = new ArrayList<>();
      for (int i = 0; i < myEvents.length; i++) {
        LinkedList<E> eventList = getEventsList(i);
        allEvents.addAll(eventList);
        eventList.clear();
      }
      allEvents.forEach(this::processRemove);
      return allEvents;
    }
    finally {
      myLock.unlock();
    }
  }

  public boolean isEmpty() {
    myLock.lock();
    try {
      return ContainerUtil.and(myEvents, AbstractCollection::isEmpty);
    }
    finally {
      myLock.unlock();
    }
  }

  public boolean hasAsyncCommands() {
    myLock.lock();
    try {
      return myAsyncCommandsCounter > 0;
    }
    finally {
      myLock.unlock();
    }
  }

  private void processAdd(@NotNull E event) {
    if (!(event instanceof PossiblySyncCommand)) {
      myAsyncCommandsCounter++;
    }
  }

  private void processRemove(@NotNull E event) {
    if (!(event instanceof PossiblySyncCommand)) {
      LOG.assertTrue(--myAsyncCommandsCounter >= 0);
    }
  }

  public void reopen() {
    myIsClosed = false;
  }
}
