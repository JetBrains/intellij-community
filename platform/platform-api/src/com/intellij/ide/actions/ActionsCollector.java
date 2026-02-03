// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

public abstract class ActionsCollector {
  public static ActionsCollector getInstance() {
    return ApplicationManager.getApplication().getService(ActionsCollector.class);
  }

  /**
   * @deprecated Don't use this method directly
   *
   * Actions executed with {@link ActionUtil#performAction} are reported automatically.
   */
  @Deprecated(forRemoval = true)
  public abstract void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context);

  /**
   * Don't use this method directly unless absolutely necessary.
   * Prefer executing action with {@link ActionUtil#performAction}
   * then it will be reported and its execution will be visible for other action listeners.
   *
   * Records action id for global actions or action class name for actions generated on runtime.
   * Only actions from platform and JB plugins are recorded.
   */
  public abstract void record(@Nullable Project project, @Nullable AnAction action, @Nullable AnActionEvent event, @Nullable Language lang);

  public abstract void onActionConfiguredByActionId(@NotNull AnAction action, @NotNull String actionId);

  public abstract void recordUpdate(@NotNull AnAction action, @NotNull AnActionEvent e, long durationMs);
}
