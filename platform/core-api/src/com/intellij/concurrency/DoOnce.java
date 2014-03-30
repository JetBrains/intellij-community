/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes given runnable exactly once.
 * <p/>
 * Author: dmitrylomov
 */
public class DoOnce {
  private final AtomicReference<Runnable> myRunnable;

  public DoOnce(@NotNull Runnable runnable) {
    myRunnable = new AtomicReference<Runnable>(runnable);
  }

  public void execute() {
    Runnable runnable = myRunnable.getAndSet(null);
    if (runnable != null) {
      runnable.run();
    }
  }
}
