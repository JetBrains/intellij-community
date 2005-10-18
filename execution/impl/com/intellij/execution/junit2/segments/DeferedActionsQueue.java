package com.intellij.execution.junit2.segments;

public interface DeferedActionsQueue {
  void addLast(Runnable runnable);

  void setDispactchListener(DispatchListener listener);
}
