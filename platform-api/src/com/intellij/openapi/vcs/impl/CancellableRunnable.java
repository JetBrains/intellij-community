package com.intellij.openapi.vcs.impl;

public interface CancellableRunnable extends Runnable {
  void cancel();
}
