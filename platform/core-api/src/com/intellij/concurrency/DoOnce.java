/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.concurrency;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes given runnable exactly once.
 * <p/>
 * Author: dmitrylomov
 */
public class DoOnce {
  private Runnable myRunnable;
  private final AtomicBoolean myAlreadyRun;

  public DoOnce(@NotNull Runnable runnable) {
    myRunnable = runnable;
    myAlreadyRun = new AtomicBoolean(false);
  }

  public void execute() {
    if (myAlreadyRun.compareAndSet(false, true)) {
      try {
        myRunnable.run();
      }
      finally {
        myRunnable = null; // do not leak runnable
      }
    }
  }
}
