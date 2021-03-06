/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.util.Function;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiffTaskQueue {
  @Nullable private ProgressIndicator myProgressIndicator;

  @RequiresEdt
  public void abort() {
    if (myProgressIndicator != null) myProgressIndicator.cancel();
    myProgressIndicator = null;
  }

  @RequiresEdt
  public void executeAndTryWait(@NotNull Function<? super ProgressIndicator, ? extends Runnable> backgroundTask,
                                @Nullable Runnable onSlowAction,
                                long waitMillis) {
    executeAndTryWait(backgroundTask, onSlowAction, waitMillis, false);
  }

  @RequiresEdt
  public void executeAndTryWait(@NotNull Function<? super ProgressIndicator, ? extends Runnable> backgroundTask,
                                @Nullable Runnable onSlowAction,
                                long waitMillis,
                                boolean forceEDT) {
    abort();
    myProgressIndicator = BackgroundTaskUtil.executeAndTryWait(backgroundTask, onSlowAction, waitMillis, forceEDT);
  }
}
