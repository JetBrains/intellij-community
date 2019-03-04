// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicLong;

public class ProfilingInfo {
  private final long myCreatedTimeStamp;
  private volatile long myDisposedTimeStamp = -1;

  private final AtomicLong myUseCount = new AtomicLong();
  @NotNull private final StackTraceElement myOrigin;

  public ProfilingInfo(@NotNull StackTraceElement origin) {
    myCreatedTimeStamp = currentTime();
    myOrigin = origin;
  }

  public synchronized void valueDisposed() {
    if (myDisposedTimeStamp != 0) {
      myDisposedTimeStamp = currentTime();
    }
  }

  public void valueUsed() {
    myUseCount.incrementAndGet();
  }

  public long getCreatedTimeStamp() {
    return myCreatedTimeStamp;
  }

  public long getDisposedTimeStamp() {
    return myDisposedTimeStamp;
  }

  public long getUseCount() {
    return myUseCount.get();
  }

  public long getLifetime() {
    long disposedTime = myDisposedTimeStamp;
    if (disposedTime == -1) disposedTime = currentTime();

    return disposedTime - myCreatedTimeStamp;
  }

  @NotNull
  public StackTraceElement getOrigin() {
    return myOrigin;
  }

  private static long currentTime() {
    return System.currentTimeMillis();
  }
}
