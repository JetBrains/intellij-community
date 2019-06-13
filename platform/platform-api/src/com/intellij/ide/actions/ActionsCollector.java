// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ActionsCollector {

  public static ActionsCollector getInstance() {
    return ServiceManager.getService(ActionsCollector.class);
  }

  /**
   * @deprecated use {@link #record(Project, AnAction, AnActionEvent)} instead
   */
  @Deprecated
  public void record(@Nullable AnAction action, @Nullable AnActionEvent event) {}

  /**
   * Records explicitly whitelisted actions
   */
  public void record(@Nullable String actionId, @NotNull Class context) {
    record(actionId, null, context);
  }

  /**
   * Records explicitly whitelisted actions with input event
   */
  public abstract void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context);

  /**
   * @deprecated use {@link #record(Project, AnAction, AnActionEvent, Language)} instead
   */
  @Deprecated
  public void record(@Nullable Project project, @Nullable AnAction action, @Nullable AnActionEvent event) {
    record(project, action, event, null);
  }

  /**
   * Records action id for global actions or action class name for actions generated on runtime.
   * Only actions from platform and JB plugins are recorded.
   */
  public abstract void record(@Nullable Project project, @Nullable AnAction action, @Nullable AnActionEvent event, @Nullable Language lang);

  public abstract void onActionConfiguredByActionId(@NotNull AnAction action, @NotNull String actionId);
}
