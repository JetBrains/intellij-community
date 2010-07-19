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

public class QueueProcessor<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.concurrency.QueueProcessor");

  private final Consumer<T> myProcessor;
  private final LinkedList<T> myQueue = new LinkedList<T>();
  private boolean isProcessing;

  public QueueProcessor(Consumer<T> processor) {
    myProcessor = processor;
  }

  public void add(T element) {
    doAdd(element, false);
  }

  public void addFirst(T element) {
    doAdd(element, true);
  }

  private void doAdd(T element, boolean atHead) {
    synchronized (myQueue) {
      if (!isProcessing) {
        isProcessing = true;
        startProcessing(element);
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

  private void startProcessing(final T element) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        doProcess(element);
      }
    });
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
