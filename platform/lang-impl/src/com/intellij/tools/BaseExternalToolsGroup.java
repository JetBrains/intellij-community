// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tools;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionGroupUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.ActionWrapperUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseExternalToolsGroup<T extends Tool> extends ActionGroup implements DumbAware {
  protected BaseExternalToolsGroup() {
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public abstract @NotNull String getDelegateGroupId();

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      event.getPresentation().setEnabledAndVisible(false);
      return;
    }
    event.getPresentation().setEnabled(true);
    event.getPresentation().setVisible(!ActionGroupUtil.isGroupEmpty(this, event));
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    ActionGroup delegate = (ActionGroup)(e != null ? e.getActionManager() : ActionManager.getInstance())
      .getAction(getDelegateGroupId());
    if (delegate == null) return EMPTY_ARRAY;
    return ActionWrapperUtil.getChildren(e, this, delegate);
  }
}
