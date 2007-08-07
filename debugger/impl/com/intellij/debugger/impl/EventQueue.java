package com.intellij.debugger.impl;

import com.intellij.openapi.diagnostic.Logger;

import java.util.LinkedList;
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

  public void put(E event, int priority) {
    LOG.assertTrue(event != null);
    if(LOG.isDebugEnabled()) {
      LOG.debug("put event " + event);
    }

    myLock.lock();
    try {
      getEventsList(priority).offer(event);
      myEventsAvailable.signal();
    }
    finally {
      myLock.unlock();
    }
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
    return myCurrentEvent = getEvent();
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
