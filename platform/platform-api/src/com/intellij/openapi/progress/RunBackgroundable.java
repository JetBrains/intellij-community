/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunBackgroundable {
  private RunBackgroundable() {
  }

  public static void run(@NotNull final Task task) {
    final ProgressManager pm = ProgressManager.getInstance();
    if (ApplicationManager.getApplication().isDispatchThread()) {
      pm.run(task);
    } else {
      runIfBackgroundThread(task, pm.getProgressIndicator(), null);
    }
  }

  public static void runIfBackgroundThread(final Task task, final ProgressIndicator pi, @Nullable final Runnable pooledContinuation) {
    boolean canceled = true;
    try {
      task.run(pi);
      canceled = pi != null && pi.isCanceled();
    } catch (ProcessCanceledException e) {
      //
    } finally {
      if (pooledContinuation != null) {
        pooledContinuation.run();
      }
    }

    final boolean finalCanceled = canceled;
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      public void run() {
        if (finalCanceled) {
          task.onCancel();
        }
        else {
          task.onSuccess();
        }
      }
    });
  }
}
