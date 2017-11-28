/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
    if (!myAlarm.isDisposed()) {
      cancelAllRequests();
      addRequest(() -> myTask.run(suspendContext));
    }
  }

  public void cancelAllRequests() {
    myAlarm.cancelAllRequests();
  }

  private void addRequest(@NotNull Runnable runnable) {
    myAlarm.addRequest(runnable, myDelayMillis);
  }

  @FunctionalInterface
  public interface Task {
    void run(@NotNull SuspendContextImpl suspendContext);
  }
}
