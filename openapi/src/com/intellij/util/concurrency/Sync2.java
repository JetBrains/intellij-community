package com.intellij.util.concurrency;

public interface Sync2 extends Sync{
  void acquire();
  boolean attempt(long msecs);
}
