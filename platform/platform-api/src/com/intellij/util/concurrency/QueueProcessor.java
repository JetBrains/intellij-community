/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.concurrency;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * <p>QueueProcessor processes elements which are being added to a queue via {@link #add(Object)} and {@link #addFirst(Object)} methods.</p>
 * <p>Elements are processed one by one in a special single thread.
 * The processor itself is passed in the constructor and is called from that thread.
 * By default processing starts when the first element is added to the queue, though there is an 'autostart' option which holds
 * the processor until {@link #start()} is called.</p>
 *
 * @param <T> type of queue elements.
 */
public class QueueProcessor<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.concurrency.QueueProcessor");

  private final PairConsumer<T, Runnable> myProcessor;
  private final LinkedList<T> myQueue = new LinkedList<T>();
  private final Runnable myContinuationContext;

  private boolean isProcessing;
  private boolean myStarted;

  private final ThreadToUse myThreadToUse;
  private final Condition<?> myDeathCondition;
  private final Map<MyOverrideEquals, ModalityState> myModalityState;

  /**
   * Constructs a QueueProcessor with the given processor and autostart setting.
   * By default QueueProcessor starts processing when it receives the first element. Pass <code>false</code> to alternate its behavior.
   *
   * @param processor processor of queue elements.
   * @param autostart if <code>true</code> (which is by default), the queue will be processed immediately when it receives the first element.
   *                  If <code>false</code>, then it will wait for the {@link #start()} command.
   *                  After QueueProcessor has started once, autostart setting doesn't matter anymore: all other elements will be processed immediately.
   */

  public QueueProcessor(final PairConsumer<T, Runnable> processor, boolean autostart, final ThreadToUse threadToUse,
                        final Condition<?> deathCondition) {
    myProcessor = processor;
    myStarted = autostart;
    myThreadToUse = threadToUse;
    myDeathCondition = deathCondition;
    myModalityState = new HashMap<MyOverrideEquals, ModalityState>();

    myContinuationContext = new Runnable() {
      @Override
      public void run() {
        synchronized (myQueue) {
          isProcessing = false;
          if (myQueue.isEmpty()) {
            myQueue.notifyAll();
          } else {
            startProcessing();
          }
        }
      }
    };
  }

  public QueueProcessor(final Consumer<T> processor, final Condition<?> deathCondition, boolean autostart) {
    this(wrappingProcessor(processor), autostart, ThreadToUse.POOLED, deathCondition);
  }

  public void add(T t, ModalityState state) {
    myModalityState.put(new MyOverrideEquals(t), state);
    doAdd(t, false);
  }

  private static<T> PairConsumer<T, Runnable> wrappingProcessor(final Consumer<T> processor) {
    return new PairConsumer<T, Runnable>() {
      @Override
      public void consume(T item, Runnable runnable) {
        try {
          processor.consume(item);
        }
        catch (Throwable e) {
          LOG.warn(e);
        }
        runnable.run();
      }
    };
  }

  /**
   * Constructs a QueueProcessor, which will autostart as soon as the first element is added to it.
   */
  public QueueProcessor(Consumer<T> processor, final Condition<?> deathCondition) {
    this(processor, deathCondition, true);
  }

  /**
   * Starts queue processing if it hasn't started yet.
   * Effective only if the QueueProcessor was created with no-autostart option: otherwise processing will start as soon as the first element
   * is added to the queue.
   * If there are several elements in the queue, processing starts from the first one.
   */
  public void start() {
    synchronized (myQueue) {
      if (myStarted) return;
      myStarted = true;
      if (! myQueue.isEmpty()) {
        startProcessing();
      }
    }
  }

  public void add(T element) {
    doAdd(element, false);
  }

  public void addFirst(T element) {
    doAdd(element, true);
  }

  private void doAdd(T element, boolean atHead) {
    synchronized (myQueue) {
      if (atHead) {
        myQueue.addFirst(element);
      } else {
        myQueue.add(element);
      }
      startProcessing();
    }
  }

  public void clear() {
    synchronized (myQueue) {
      myQueue.clear();
    }
  }

  public void waitFor() {
    synchronized (myQueue) {
      while (isProcessing) {
        try {
          myQueue.wait();
        } catch (InterruptedException e) {
          //ok
        }
      }
    }
  }

  private boolean startProcessing() {
    LOG.assertTrue(Thread.holdsLock(myQueue));

    if (isProcessing || ! myStarted) {
      return false;
    }
    isProcessing = true;
    final T item = myQueue.removeFirst();
    final Runnable runnable = new Runnable() {
      @Override
      public void run() {
        if (myDeathCondition.value(null)) return;
        try {
          myProcessor.consume(item, myContinuationContext);
        } catch (Throwable t) {
          LOG.warn(t);
        }
      }
    };
    final Application application = ApplicationManager.getApplication();
    if (ThreadToUse.AWT.equals(myThreadToUse)) {
      final ModalityState state = myModalityState.remove(new MyOverrideEquals(item));
      if (state != null) {
        application.invokeLater(runnable, state);
      } else {
        application.invokeLater(runnable);
      }
    } else {
      application.executeOnPooledThread(runnable);
    }
    return true;
  }

  public boolean isEmpty() {
    synchronized (myQueue) {
      return myQueue.isEmpty() && (! isProcessing);
    }
  }

  public static enum ThreadToUse {
    AWT,
    POOLED
  }

  private static class MyOverrideEquals {
    private final Object myDelegate;

    private MyOverrideEquals(Object delegate) {
      myDelegate = delegate;
    }

    @Override
    public int hashCode() {
      return myDelegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return ((MyOverrideEquals) obj).myDelegate == myDelegate;
    }
  }
}
