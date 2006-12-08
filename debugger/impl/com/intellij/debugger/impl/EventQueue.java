package com.intellij.debugger.impl;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class EventQueue<E> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.EventQueue");

  private final ConcurrentLinkedQueue[] myEvents;
  private final ReentrantLock myLock;
  private final Condition myEventsAvailable;

  private volatile E myCurrentEvent;

  private volatile boolean myIsClosed = false;

  public EventQueue (int countPriorities) {
    myLock = new ReentrantLock();
    myEventsAvailable = myLock.newCondition();
    myEvents = new ConcurrentLinkedQueue[countPriorities];
    for (int i = 0; i < myEvents.length; i++) {
      myEvents[i] = new ConcurrentLinkedQueue<E>();
    }
  }

  public void put(E event, int priority) {
    LOG.assertTrue(event != null);
    if(LOG.isDebugEnabled()) {
      LOG.debug("put event " + event);
    }

    myEvents[priority].offer(event);
    myLock.lock();
    try {
      myEventsAvailable.signal();
    }
    finally {
      myLock.unlock();
    }
  }

  public void close(){
    myIsClosed = true;
    myLock.lock();
    try {
      myEventsAvailable.signalAll();
    }
    finally {
      myLock.unlock();
    }
  }

  @Nullable
  private E getEvent() throws EventQueueClosedException {
    for (int i = 0; i < myEvents.length; i++) {
      final E event = ((ConcurrentLinkedQueue<E>)myEvents[i]).poll();
      if(event != null) {
        return event;
      }
    }

    if(myIsClosed) {
      throw new EventQueueClosedException();
    }

    return null;
  }

  public E get() throws EventQueueClosedException {
    E event = getEvent();
    while (event == null) {
      myLock.lock();
      try {
        myEventsAvailable.await();
      }
      catch (InterruptedException ignored) {
      }
      finally {
        myLock.unlock();
      }
      event = getEvent();
    }

    myCurrentEvent = event;
    return event;
  }

  public boolean isClosed() {
    return myIsClosed;
  }

  public static interface EventGetter<E> {
    void event(E event);
  }

  public void getCurrentEvent(EventGetter<E> getter) {
    getter.event(myCurrentEvent);
  }
}
