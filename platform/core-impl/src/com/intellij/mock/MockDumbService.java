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
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class MockDumbService extends DumbService {
  private final Project myProject;

  public MockDumbService(Project project) {
    myProject = project;
  }

  @Override
  public ModificationTracker getModificationTracker() {
    return new SimpleModificationTracker();
  }

  @Override
  public boolean isDumb() {
    return false;
  }

  @Override
  public void runWhenSmart(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void waitForSmartMode() {
  }

  @Override
  public void queueTask(@NotNull DumbModeTask task) {
    task.performInDumbMode(new EmptyProgressIndicator());
    Disposer.dispose(task);
  }

  @Override
  public void cancelTask(@NotNull DumbModeTask task) { }

  @Override
  public void completeJustSubmittedTasks() {
  }

  @Override
  public JComponent wrapGently(@NotNull JComponent dumbUnawareContent, @NotNull Disposable parentDisposable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showDumbModeNotification(@NotNull String message) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void setAlternativeResolveEnabled(boolean enabled) {
  }

  @Override
  public boolean isAlternativeResolveEnabled() {
    return false;
  }

  @Override
  public void suspendIndexingAndRun(@NotNull String activityName, @NotNull Runnable activity) {
    activity.run();
  }

  @Override
  public void smartInvokeLater(@NotNull final Runnable runnable) {
    runnable.run();
  }

  @Override
  public void smartInvokeLater(@NotNull final Runnable runnable, @NotNull ModalityState modalityState) {
    runnable.run();
  }
}
