/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Please note - rejected results are not collected.
 */
public final class CollectingAsyncResult<T> {
  private final List<AsyncResult<T>> asyncResults = new SmartList<AsyncResult<T>>();

  public void add(@NotNull AsyncResult<T> callback) {
    asyncResults.add(callback);
  }

  @NotNull
  public AsyncResult<List<T>> create() {
    int size = asyncResults.size();
    if (size == 0) {
      return AsyncResult.doneList();
    }

    final List<T> results = size == 1 ? new SmartList<T>() : new ArrayList<T>(size);
    final AsyncResult<List<T>> totalResult = new AsyncResult<List<T>>(asyncResults.size(), results);
    Consumer<T> resultConsumer = new Consumer<T>() {
      @Override
      public void consume(T result) {
        synchronized (results) {
          results.add(result);
        }
        totalResult.setDone();
      }
    };
    for (AsyncResult<T> subResult : asyncResults) {
      subResult.doWhenDone(resultConsumer).notifyWhenRejected(totalResult);
    }
    return totalResult;
  }

  public boolean isEmpty() {
    return asyncResults.isEmpty();
  }
}