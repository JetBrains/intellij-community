package com.intellij.openapi.util;

import java.util.concurrent.atomic.AtomicLong;

public class DefaultModificationTracker implements ModificationTracker {
  private volatile AtomicLong myCount = new AtomicLong();

  @Override
  public long getModificationCount() {
    return myCount.get();
  }

  public void incModificationCount() {
    myCount.incrementAndGet();
  }
}
