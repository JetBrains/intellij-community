// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.executors.ExecutorGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

@ApiStatus.Internal
public class ExecutorGroupActionGroup extends ActionGroup implements DumbAware {
  protected final ExecutorGroup<?> myExecutorGroup;
  private final Function<? super Executor, ? extends AnAction> myChildConverter;

  public ExecutorGroupActionGroup(@NotNull ExecutorGroup<?> executorGroup,
                                  @NotNull Function<? super Executor, ? extends AnAction> childConverter) {
    myExecutorGroup = executorGroup;
    myChildConverter = childConverter;
    Presentation presentation = getTemplatePresentation();
    presentation.setText(executorGroup.getStartActionText());
    presentation.setIconSupplier(executorGroup::getIcon);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return getChildren();
  }

  public AnAction @NotNull [] getChildren() {
    // RunExecutorSettings configurations can be modified, so we request current childExecutors on each call
    List<Executor> childExecutors = myExecutorGroup.childExecutors();
    AnAction[] result = new AnAction[childExecutors.size()];
    for (int i = 0; i < childExecutors.size(); i++) {
      result[i] = myChildConverter.apply(childExecutors.get(i));
    }
    return result;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || !project.isInitialized() || project.isDisposed()) {
      e.getPresentation().setEnabled(false);
      return;
    }
    if (!myExecutorGroup.isApplicable(project)) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    super.update(e);
  }
}
