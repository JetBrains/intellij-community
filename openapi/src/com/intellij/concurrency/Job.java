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