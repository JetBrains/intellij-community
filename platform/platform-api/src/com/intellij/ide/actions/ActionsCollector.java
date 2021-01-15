// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

/**
 * @author Konstantin Bulenkov
 */
public abstract class ActionsCollector {
  public static ActionsCollector getInstance() {
    return ApplicationManager.getApplication().getService(ActionsCollector.class);
  }

  /**
   * Records explicitly whitelisted actions with input event
   */
  public abstract void record(@Nullable String actionId, @Nullable InputEvent event, @NotNull Class context);

  /**
   * Records action id for global actions or action class name for actions generated on runtime.
   * Only actions from platform and JB plugins are recorded.
   */
  public abstract void record(@Nullable Project project, @Nullable AnAction action, @Nullable AnActionEvent event, @Nullable Language lang);

  public abstract void onActionConfiguredByActionId(@NotNull AnAction action, @NotNull String actionId);
}
