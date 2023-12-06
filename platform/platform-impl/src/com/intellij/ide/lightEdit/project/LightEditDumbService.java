// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.project;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import kotlinx.coroutines.CoroutineScope;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class LightEditDumbService extends DumbServiceImpl {
  public LightEditDumbService(Project project, CoroutineScope scope) {
    super(project, scope);
  }

  @Override
  public boolean isDumb() {
    return true;
  }

  @Override
  public void runWhenSmart(@NotNull Runnable runnable) {
    reportUnavailable();
  }

  @Override
  public void waitForSmartMode() {
    reportUnavailable();
  }

  @Override
  public void smartInvokeLater(@NotNull Runnable runnable) {
    reportUnavailable();
  }

  @Override
  public void smartInvokeLater(@NotNull Runnable runnable,
                               @NotNull ModalityState modalityState) {
    reportUnavailable();
  }

  @Override
  public void queueTask(@NotNull DumbModeTask task) {
    reportUnavailable();
  }

  @Override
  public void cancelTask(@NotNull DumbModeTask task) {
    reportUnavailable();
  }

  @Override
  public void completeJustSubmittedTasks() {
    reportUnavailable();
  }

  @Override
  public JComponent wrapGently(@NotNull JComponent dumbUnawareContent,
                               @NotNull Disposable parentDisposable) {
    reportUnavailable();
    return null;
  }

  @Override
  public JComponent wrapWithSpoiler(@NotNull JComponent dumbAwareContent,
                                    @NotNull Runnable updateRunnable,
                                    @NotNull Disposable parentDisposable) {
    reportUnavailable();
    return null;
  }

  @Override
  public void setAlternativeResolveEnabled(boolean enabled) {

  }

  @Override
  public boolean isAlternativeResolveEnabled() {
    return false;
  }

  @Override
  public void suspendIndexingAndRun(@Nls(capitalization = Nls.Capitalization.Sentence) @NotNull String activityName,
                                    @NotNull Runnable activity) {
    reportUnavailable();
  }

  @Override
  public void unsafeRunWhenSmart(@NotNull Runnable runnable) {
    reportUnavailable();
  }

  private static void reportUnavailable() {
    throw new UnsupportedOperationException("Smart mode is not available when LightEdit is active");
  }

  @Override
  public long getModificationCount() {
    return 0;
  }
}
