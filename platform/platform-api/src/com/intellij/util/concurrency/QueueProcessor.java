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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Consumer;

import java.util.LinkedList;

/**
 * <p>QueueProcessor processes elements which are being added to a queue via {@link #add(Object)} and {@link #addFirst(Object)} methods.</p>
 * <p>Elements are processed one by one in a special single thread.
 * The processor itself is passed in the constructor and is called from that thread.
 * By default processing starts when the first element is added to the queue, though there is an 'autostart' option which holds
 * the processor until {@link #start()} is called.</p>
 * @param <T> type of queue elements.
 */
public class QueueProcessor<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.concurrency.QueueProcessor");

  private final Consumer<T> myProcessor;
  private final LinkedList<T> myQueue = new LinkedList<T>();
  private boolean isProcessing;
  private boolean myStarted;

  /**
   * Constructs a QueueProcessor with the given processor and autostart setting.
   * By default QueueProcessor starts processing when it receives the first element. Pass <code>false</code> to alternate its behavior.
   *
   * @param processor processor of queue elements.
   * @param autostart if <code>true</code> (which is by default), the queue will be processed immediately when it receives the first element.
   * If <code>false</code>, then it will wait for the {@link #start()} command.
   * After QueueProcessor has started once, autostart setting doesn't matter anymore: all other elements will be processed immediately.
   */
  public QueueProcessor(Consumer<T> processor, boolean autostart) {
    myProcessor = processor;
    myStarted = autostart;
  }

  /**
   * Constructs a QueueProcessor, which will autostart as soon as the first element is added to it.
   */
  public QueueProcessor(Consumer<T> processor) {
    this(processor, true);
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
      if (!myQueue.isEmpty()) {
        startProcessing(myQueue.poll());
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
      if (startProcessing(element)) {
        return;
      }

      if (atHead) {
        myQueue.addFirst(element);
      }
      else {
        myQueue.add(element);
      }
    }
  }

  public void clear() {
    synchronized (myQueue) {
      myQueue.clear();
    }
  }

  public void waitFor() throws InterruptedException {
    synchronized (myQueue) {
      while (isProcessing) {
        myQueue.wait();
      }
    }
  }

  private boolean startProcessing(final T element) {
    LOG.assertTrue(Thread.holdsLock(myQueue));

    if (isProcessing || !myStarted) {
      return false;
    }
    isProcessing = true;
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        doProcess(element);
      }
    });
    return true;
  }

  private void doProcess(T next) {
    while (true) {
      try {
        myProcessor.consume(next);
      }
      catch (Exception e) {
        LOG.warn(e);
      }

      synchronized (myQueue) {
        next = myQueue.poll();
        if (next == null) {
          isProcessing = false;
          myQueue.notifyAll();
          return;
        }
      }
    }
  }
}
