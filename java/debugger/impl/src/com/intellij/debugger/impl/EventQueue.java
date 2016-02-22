/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.impl;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class EventQueue<E> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.EventQueue");

  private final LinkedList[] myEvents;
  private final ReentrantLock myLock;
  private final Condition myEventsAvailable;

  private volatile E myCurrentEvent;

  private volatile boolean myIsClosed = false;

  public EventQueue (int countPriorities) {
    myLock = new ReentrantLock();
    myEventsAvailable = myLock.newCondition();
    myEvents = new LinkedList[countPriorities];
    for (int i = 0; i < myEvents.length; i++) {
      myEvents[i] = new LinkedList<E>();
    }
  }

  public boolean pushBack(@NotNull E event, int priority) {
    if(LOG.isDebugEnabled()) {
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
    if(LOG.isDebugEnabled()) {
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

  public void close(){
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
        if(myIsClosed) {
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

  @NotNull
  public List<E> clearQueue() {
    final List<E> allEvents = new ArrayList<>();
    for (int i = 0; i < myEvents.length; i++) {
      final LinkedList<E> eventList = getEventsList(i);
      for (E event = eventList.poll(); event != null; event = eventList.poll()) {
        allEvents.add(event);
      }
    }
    return allEvents;
  }

  public void reopen() {
    myIsClosed = false;
  }
}
