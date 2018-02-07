// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.utils;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.openapi.Disposable;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

public class SingleAlarmWithMutableDelay {
  private final Alarm myAlarm;
  private final Task myTask;

  private volatile int myDelayMillis;

  public SingleAlarmWithMutableDelay(@NotNull Task task, @NotNull Disposable parentDisposable) {
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable);
    myTask = task;
  }

  public void setDelay(int millis) {
    myDelayMillis = millis;
  }

  public void cancelAndRequest(@NotNull SuspendContextImpl suspendContext) {
    cancelAndAddRequest(suspendContext, myDelayMillis);
  }

  public void cancelAndRequestImmediate(@NotNull SuspendContextImpl suspendContext) {
    cancelAndAddRequest(suspendContext, 0);
  }

  public void cancelAllRequests() {
    myAlarm.cancelAllRequests();
  }

  private void cancelAndAddRequest(@NotNull SuspendContextImpl suspendContext, int delayMillis) {
    if (!myAlarm.isDisposed()) {
      cancelAllRequests();
      myAlarm.addRequest(() -> myTask.run(suspendContext), delayMillis);
    }
  }

  @FunctionalInterface
  public interface Task {
    void run(@NotNull SuspendContextImpl suspendContext);
  }
}
