/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.util.Consumer;

import java.util.HashMap;
import java.util.Map;

/**
 * @author irengrig
 *         Date: 2/28/11
 *         Time: 3:41 PM
 */
public class QueueProcessorRemovePartner<Key, Task> {
  private final Map<Key, Task> myMap;
  private final QueueProcessor<Key> myProcessor;
  private final Consumer<Task> myConsumer;
  private final Object myLock;

  public QueueProcessorRemovePartner(final Project project, Consumer<Task> consumer) {
    myConsumer = consumer;
    myMap = new HashMap<>();
    myLock = new Object();
    myProcessor = new QueueProcessor<>(key -> {
      final Task task;
      synchronized (myLock) {
        task = myMap.remove(key);
      }
      if (task != null) {
        myConsumer.consume(task);
      }
    }, project.getDisposed(), true);
  }

  public void add(final Key key, final Task task) {
    synchronized (myLock) {
      myMap.put(key, task);
    }
    myProcessor.add(key);
  }

  public void remove(final Key key) {
    synchronized (myLock) {
      myMap.remove(key);
    }
  }

  public boolean isEmpty() {
    synchronized (myLock) {
      return myMap.isEmpty();
    }
  }

  public void clear() {
    synchronized (myLock) {
      myMap.clear();
    }
  }

  public boolean containsKey(final Key key) {
    synchronized (myLock) {
      return myMap.containsKey(key);
    }
  }
}
