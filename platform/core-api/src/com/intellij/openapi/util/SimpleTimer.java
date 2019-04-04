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
package com.intellij.openapi.util;

import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Simple timer that keeps order of scheduled tasks
 */
public class SimpleTimer {
  private static final SimpleTimer ourInstance = newInstance("Shared");

  // restrict threads running tasks to one since same-delay-tasks must be executed sequentially
  private final ScheduledExecutorService myScheduledExecutorService = AppExecutorUtil.createBoundedScheduledExecutorService(
    "SimpleTimer Pool", 1);
  @NotNull private final String myName;

  private SimpleTimer(@NotNull String name) {
    myName = name;
  }

  public static SimpleTimer getInstance() {
    return ourInstance;
  }
  
  public static SimpleTimer newInstance(@NotNull String name) {
    return new SimpleTimer(name);
  }

  @NotNull
  public SimpleTimerTask setUp(@NotNull final Runnable runnable, final long delay) {
    final ScheduledFuture<?> future = myScheduledExecutorService.schedule(runnable, delay, TimeUnit.MILLISECONDS);
    return new SimpleTimerTask() {
      @Override
      public void cancel() {
        future.cancel(false);
      }

      @Override
      public boolean isCancelled() {
        return future.isCancelled();
      }
    };
  }

  @Override
  public String toString() {
    return "SimpleTimer "+myName;
  }
}