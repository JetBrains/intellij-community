/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.concurrency;

import java.util.List;
import java.util.concurrent.Callable;

public interface Job<T> {
  // the lower the priority the more important the task is
  int DEFAULT_PRIORITY = 100;

  String getTitle();

  void addTask(Callable<T> task);

  void addTask(Runnable task, T result);

  void addTask(Runnable task);

  List<T> scheduleAndWaitForResults() throws Throwable;

  void cancel();

  boolean isCanceled();

  void schedule();

  boolean isDone();
}