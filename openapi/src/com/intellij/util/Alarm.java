/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.LongArrayList;

import java.util.ArrayList;
import java.util.Iterator;

public class Alarm {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.Alarm");

  private final Object LOCK = new Object();

  private final MyThread myThread;
  private HashMap<Runnable, Runnable> myOriginalToThreadRequestMap = new HashMap<Runnable, Runnable>();

  private static MyThread ourThreadNormal = new MyThread(false);
  private static MyThread ourThreadUseSwing = new MyThread(true);

  static {
    ourThreadNormal.start();
    ourThreadUseSwing.start();
  }

  public static class ThreadToUse {
    public static final ThreadToUse SWING_THREAD = new ThreadToUse("SWING_THREAD");
    public static final ThreadToUse SHARED_THREAD = new ThreadToUse("SHARED_THREAD");
    public static final ThreadToUse OWN_THREAD = new ThreadToUse("OWN_THREAD");

    private final String myName;

    private ThreadToUse(String name) {
      myName = name;
    }

    public String toString() {
      return myName;
    }
  }

  /**
   * Creates alarm that works in Swing thread
   */
  public Alarm() {
    this(ThreadToUse.SWING_THREAD);
  }

  public Alarm(ThreadToUse threadToUse) {
    if (threadToUse == ThreadToUse.SWING_THREAD) {
      myThread = ourThreadUseSwing;
    }
    else if (threadToUse == ThreadToUse.SHARED_THREAD) {
      myThread = ourThreadNormal;
    }
    else {
      myThread = new MyThread(false);
      myThread.start();
    }
  }

  public void addRequest(final Runnable request, int delay) {
    _addRequest(request, delay, myThread == ourThreadUseSwing ? ModalityState.current() : null);
  }

  public void addRequest(final Runnable request, int delay, ModalityState modalityState) {
    LOG.assertTrue(myThread == ourThreadUseSwing);
    _addRequest(request, delay, modalityState);
  }

  private void _addRequest(final Runnable request, int delay, ModalityState modalityState) {
    synchronized (LOCK) {
      Runnable request1 = new Runnable() {
        public void run() {
          synchronized (LOCK) {
            myOriginalToThreadRequestMap.remove(request);
          }
          try {
            request.run();
          }
          catch (Exception e) {
            LOG.error(e);
          }
        }
      };
      myOriginalToThreadRequestMap.put(request, request1);
      myThread.addRequest(request1, delay, modalityState);
    }
  }

  public boolean cancelRequest(Runnable request) {
    synchronized (LOCK) {
      Runnable request1 = myOriginalToThreadRequestMap.get(request);
      if (request1 == null) return false;
      boolean success = myThread.cancelRequest(request1);
      if (success) {
        myOriginalToThreadRequestMap.remove(request);
      }
      return success;
    }
  }

  public int cancelAllRequests() {
    synchronized (LOCK) {
      int count = 0;
      Iterator<Runnable> iterator = myOriginalToThreadRequestMap.values().iterator();
      while (iterator.hasNext()) {
        Runnable request = iterator.next();
        if (myThread.cancelRequest(request)) {
          count++;
        }
      }
      myOriginalToThreadRequestMap.clear();
      return count;
    }
  }

  public int getActiveRequestCount() {
    synchronized (LOCK) {
      return myOriginalToThreadRequestMap.size();
    }
  }

  private static class MyThread extends Thread {
    private int myIdCounter = 0;

    private final Object LOCK = new Object();

    private final boolean myUseSwingThread;

    private LongArrayList myRequestIds = new LongArrayList();
    private ArrayList<Runnable> myRequests = new ArrayList<Runnable>(); // ArrayList of Runnable
    private LongArrayList myRequestTimes = new LongArrayList();
    private ArrayList<Boolean> myRequestEnqueuedFlags = new ArrayList<Boolean>();
    private ArrayList<ModalityState> myRequestModalityStates = new ArrayList<ModalityState>();

    public MyThread(boolean useSwingThread) {
      super("AlarmThread");
      myUseSwingThread = useSwingThread;
    }

    public void addRequest(Runnable request, int delay, ModalityState modalityState) {
      synchronized (LOCK) {
        myRequestIds.add(myIdCounter++);
        myRequests.add(request);
        myRequestTimes.add(System.currentTimeMillis() + delay);
        myRequestEnqueuedFlags.add(Boolean.FALSE);
        myRequestModalityStates.add(modalityState);
        if (myUseSwingThread){
          LOG.assertTrue(modalityState != null);
        }
        LOCK.notifyAll();
      }
    }

    public boolean cancelRequest(Runnable request) {
      synchronized (LOCK) {
        int index = myRequests.indexOf(request);
        if (index < 0) return false;
        myRequestIds.remove(index);
        myRequests.remove(index);
        myRequestTimes.remove(index);
        myRequestEnqueuedFlags.remove(index);
        myRequestModalityStates.remove(index);
        return true;
      }
    }

    public void run() {
      while (true) {
        long delay;
        long requestId = -1;
        synchronized (LOCK) {
          long minTime = Long.MAX_VALUE;
          long minRequestId = -1;
          for (int i = 0; i < myRequests.size(); i++) {
            Boolean isEnqueued = myRequestEnqueuedFlags.get(i);
            if (isEnqueued.booleanValue()) continue;
            long time = myRequestTimes.get(i);
            if (time < minTime) {
              minTime = time;
              minRequestId = myRequestIds.get(i);
            }
          }

          if (minTime == Long.MAX_VALUE) {
            try {
              LOCK.wait();
            }
            catch (InterruptedException e) {
            }
            continue;
          }

          long time = System.currentTimeMillis();
          delay = minTime - time;
          if (delay <= 0) {
            requestId = minRequestId;
          }
        }

        if (requestId >= 0) {
          final long _requestId = requestId;
          Runnable runnable = new Runnable() {
            public void run() {
              boolean isCanceled;
              Runnable request = null;
              synchronized (LOCK) {
                int index = myRequestIds.indexOf(_requestId);
                isCanceled = index < 0;
                if (!isCanceled) {
                  myRequestIds.remove(index);
                  request = myRequests.remove(index);
                  myRequestTimes.remove(index);
                  myRequestEnqueuedFlags.remove(index);
                  myRequestModalityStates.remove(index);
                }
              }
              if (!isCanceled) {
                request.run();
              }
            }
          };

          if (myUseSwingThread) {
            synchronized (LOCK) {
              int index = myRequestIds.indexOf(requestId);
              if (index >= 0) {
                ModalityState modalityState = myRequestModalityStates.get(index);
                myRequestEnqueuedFlags.set(index, Boolean.TRUE);
                ApplicationManager.getApplication().invokeLater(runnable, modalityState);
              }
            }
          }
          else {
            runnable.run();
          }
        }
        else {
          if (delay > 0) {
            synchronized (LOCK) {
              try {
                LOCK.wait(delay);
              }
              catch (InterruptedException e) {
              }
            }
          }
        }
      }
    }
  }
}