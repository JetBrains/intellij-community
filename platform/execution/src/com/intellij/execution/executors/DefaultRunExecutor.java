// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.executors;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author spleaner
 */
public class DefaultRunExecutor extends Executor {
  @NonNls public static final String EXECUTOR_ID = ToolWindowId.RUN;

  @Override
  @NotNull
  public String getStartActionText() {
    return ExecutionBundle.message("default.runner.start.action.text");
  }

  @NotNull
  @Override
  public String getToolWindowId() {
    return ToolWindowId.RUN;
  }

  @NotNull
  @Override
  public Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowRun;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return AllIcons.Actions.Execute;
  }

  @Override
  @NotNull
  public Icon getRerunIcon() {
    return AllIcons.Actions.Rerun;
  }

  @Override
  public Icon getDisabledIcon() {
    return IconLoader.getDisabledIcon(getIcon());
  }

  @Override
  public String getDescription() {
    return ExecutionBundle.message("standard.runner.description");
  }

  @Override
  @NotNull
  public String getActionName() {
    return ExecutionBundle.message("tool.window.name.run");
  }

  @Override
  @NotNull
  public String getId() {
    return EXECUTOR_ID;
  }

  @Override
  public String getContextActionId() {
    return "RunClass";
  }

  @Override
  public String getHelpId() {
    return "ideaInterface.run";
  }

  @Override
  public boolean isSupportedOnTarget() {
    return EXECUTOR_ID.equalsIgnoreCase(getId());
  }

  public static Executor getRunExecutorInstance() {
    return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID);
  }
}
