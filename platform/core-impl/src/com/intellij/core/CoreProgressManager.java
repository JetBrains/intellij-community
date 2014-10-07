/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.openapi.progress.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.concurrent.atomic.AtomicReference;

class CoreProgressManager extends ProgressManager {
  private static final ThreadLocal<ProgressIndicator> myIndicator = new ThreadLocal<ProgressIndicator>();
  @Override
  public boolean hasProgressIndicator() {
    return getProgressIndicator() != null;
  }

  @Override
  public boolean hasModalProgressIndicator() {
    return false;
  }

  @Override
  public boolean hasUnsafeProgressIndicator() {
    return false;
  }

  @Override
  public void runProcess(@NotNull final Runnable process, final ProgressIndicator progress) throws ProcessCanceledException {
    executeProcessUnderProgress(new Runnable(){
      @Override
      public void run() {
        try {
          if (progress != null && !progress.isRunning()) {
            progress.start();
          }
          process.run();
        }
        finally {
          if (progress != null && progress.isRunning()) {
            progress.stop();
          }
        }
      }
    },progress);
  }

  @Override
  public <T> T runProcess(@NotNull final Computable<T> process, ProgressIndicator progress) throws ProcessCanceledException {
    final AtomicReference<T> result = new AtomicReference<T>();
    executeProcessUnderProgress(new Runnable() {
      @Override
      public void run() {
        result.set(process.compute());
      }
    }, progress);
    return result.get();
  }

  @Override
  public void executeNonCancelableSection(@NotNull Runnable runnable) {
    executeProcessUnderProgress(runnable, new EmptyProgressIndicator());
  }

  @Override
  public void setCancelButtonText(String cancelButtonText) {

  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull @Nls String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project) {
    executeProcessUnderProgress(process, new EmptyProgressIndicator());
    return true;
  }

  @Override
  public <T, E extends Exception> T runProcessWithProgressSynchronously(@NotNull final ThrowableComputable<T, E> process,
                                                                        @NotNull @Nls String progressTitle,
                                                                        boolean canBeCanceled,
                                                                        @Nullable Project project) throws E {
    final AtomicReference<T> result = new AtomicReference<T>();
    final AtomicReference<E> exception = new AtomicReference<E>();
    executeProcessUnderProgress(new Runnable() {
      @Override
      public void run() {
        try {
          result.set(process.compute());
        }
        catch (Exception e) {
          exception.set((E)e);
        }
      }
    }, new EmptyProgressIndicator());
    if (exception.get() != null) throw exception.get();
    return result.get();
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull @Nls String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project,
                                                     @Nullable JComponent parentComponent) {
    return runProcessWithProgressSynchronously(process, progressTitle, canBeCanceled, project);
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull @Nls String progressTitle,
                                                   @NotNull Runnable process,
                                                   @Nullable Runnable successRunnable,
                                                   @Nullable Runnable canceledRunnable) {
    runProcess(process, new EmptyProgressIndicator());
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull @Nls String progressTitle,
                                                   @NotNull Runnable process,
                                                   @Nullable Runnable successRunnable,
                                                   @Nullable Runnable canceledRunnable,
                                                   @NotNull PerformInBackgroundOption option) {
    runProcess(process, new EmptyProgressIndicator());
  }

  @Override
  public void run(@NotNull final Task task) {
    runProcess(new Runnable() {
      @Override
      public void run() {
        task.run(getProgressIndicator());
      }
    }, new EmptyProgressIndicator());
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task, @NotNull ProgressIndicator progressIndicator) {
    run(task);
  }

  @Override
  public ProgressIndicator getProgressIndicator() {
    return myIndicator.get();
  }

  @Override
  protected void doCheckCanceled() throws ProcessCanceledException {
    ProgressIndicator indicator = getProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
    }
  }

  @Override
  public void executeProcessUnderProgress(@NotNull Runnable process,
                                          @Nullable("null means reuse current progress") ProgressIndicator progress)
    throws ProcessCanceledException {
    ProgressIndicator old = null;
    if (progress != null) {
      old = getProgressIndicator();
      myIndicator.set(progress);
    }
    try {
      process.run();
    }
    finally {
      if (progress != null) {
        myIndicator.set(old);
      }
    }
  }

  @NotNull
  @Override
  public NonCancelableSection startNonCancelableSection() {
    return NonCancelableSection.EMPTY;
  }
}
