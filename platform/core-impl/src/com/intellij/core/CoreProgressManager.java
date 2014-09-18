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

class CoreProgressManager extends ProgressManager {
  @Override
  public boolean hasProgressIndicator() {
    return false;
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
  public void runProcess(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    process.run();
  }

  @Override
  public <T> T runProcess(@NotNull Computable<T> process, ProgressIndicator progress) throws ProcessCanceledException {
    return process.compute();
  }

  @Override
  public void executeNonCancelableSection(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void setCancelButtonText(String cancelButtonText) {

  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull @Nls String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project) {
    process.run();
    return true;
  }

  @Override
  public <T, E extends Exception> T runProcessWithProgressSynchronously(@NotNull ThrowableComputable<T, E> process,
                                                                        @NotNull @Nls String progressTitle,
                                                                        boolean canBeCanceled,
                                                                        @Nullable Project project) throws E {
    return process.compute();
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Runnable process,
                                                     @NotNull @Nls String progressTitle,
                                                     boolean canBeCanceled,
                                                     @Nullable Project project,
                                                     @Nullable JComponent parentComponent) {
    process.run();
    return true;
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull @Nls String progressTitle,
                                                   @NotNull Runnable process,
                                                   @Nullable Runnable successRunnable,
                                                   @Nullable Runnable canceledRunnable) {
    process.run();
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Project project,
                                                   @NotNull @Nls String progressTitle,
                                                   @NotNull Runnable process,
                                                   @Nullable Runnable successRunnable,
                                                   @Nullable Runnable canceledRunnable,
                                                   @NotNull PerformInBackgroundOption option) {
    process.run();
  }

  @Override
  public void run(@NotNull Task task) {
    task.run(getProgressIndicator());
  }

  @Override
  public void runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task, @NotNull ProgressIndicator progressIndicator) {
    run(task);
  }

  @Override
  public ProgressIndicator getProgressIndicator() {
    return new EmptyProgressIndicator();
  }

  @Override
  protected void doCheckCanceled() throws ProcessCanceledException {
  }

  @NotNull
  @Override
  public NonCancelableSection startNonCancelableSection() {
    return NonCancelableSection.EMPTY;
  }
}
