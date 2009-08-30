package com.intellij.execution.junit2.segments;

public interface DispatchListener {
  void onStarted();
  void onFinished();

  DispatchListener DEAF = new DispatchListener() {
    public void onStarted() {
    }

    public void onFinished() {
    }
  };
}
