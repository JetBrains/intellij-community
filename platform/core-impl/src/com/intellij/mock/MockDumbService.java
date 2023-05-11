// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SimpleModificationTracker;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function1;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

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
  public void waitForSmartMode() { }

  @Override
  public void queueTask(@NotNull DumbModeTask task) {
    task.performInDumbMode(new EmptyProgressIndicator());
    Disposer.dispose(task);
  }

  @Override
  public void cancelTask(@NotNull DumbModeTask task) { }

  @Override
  public void cancelAllTasksAndWait() { }

  @Override
  public void completeJustSubmittedTasks() { }

  @Override
  public JComponent wrapGently(@NotNull JComponent dumbUnawareContent, @NotNull Disposable parentDisposable) {
    throw new UnsupportedOperationException();
  }

  @Override
  public JComponent wrapWithSpoiler(@NotNull JComponent dumbAwareContent,
                                    @NotNull Runnable updateRunnable,
                                    @NotNull Disposable parentDisposable) {
    return null;
  }

  @Override
  public void showDumbModeNotification(@NotNull String message) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void showDumbModeActionBalloon(@NotNull String balloonText, @NotNull Runnable runWhenSmartAndBalloonUnhidden) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public void setAlternativeResolveEnabled(boolean enabled) { }

  @Override
  public boolean isAlternativeResolveEnabled() {
    return false;
  }

  @Override
  public void suspendIndexingAndRun(@NotNull String activityName, @NotNull Runnable activity) {
    activity.run();
  }

  @Nullable
  @Override
  public Object suspendIndexingAndRun(@NotNull @NlsContexts.ProgressText String activityName,
                                      @NotNull Function1<? super Continuation<? super Unit>, ?> activity,
                                      @NotNull Continuation<? super Unit> $completion) {
    return activity.invoke($completion);
  }

  @Override
  public void unsafeRunWhenSmart(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void smartInvokeLater(@NotNull final Runnable runnable) {
    runnable.run();
  }

  @Override
  public void smartInvokeLater(@NotNull final Runnable runnable, @NotNull ModalityState modalityState) {
    runnable.run();
  }

  @Override
  public AccessToken runWithWaitForSmartModeDisabled() {
    return AccessToken.EMPTY_ACCESS_TOKEN;
  }
}
