package com.intellij.openapi.wm.ex;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.util.containers.DoubleArrayList;
import com.intellij.util.containers.Stack;

public interface ProgressIndicatorEx extends ProgressIndicator {

  void addStateDelegate(ProgressIndicatorEx delegate);

  void initStateFrom(ProgressIndicatorEx indicator);

  Stack<String> getTextStack();

  DoubleArrayList getFractionStack();

  Stack<String> getText2Stack();

  int getNonCancelableCount();

  boolean isModalityEntered();

  void finish(final Task task);
  
  boolean isFinished(final TaskInfo task);

  boolean wasStarted();
}
