// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.execution.executors;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class DefaultRunExecutor extends Executor {
  public static final @NonNls String EXECUTOR_ID = ToolWindowId.RUN;

  @Override
  public @NotNull String getStartActionText() {
    return ExecutionBundle.message("default.runner.start.action.text");
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Title) @NotNull String getStartActionText(@NotNull String configurationName) {
    if (configurationName.isEmpty()) return getStartActionText();
    return TextWithMnemonic.parse(ExecutionBundle.message("default.runner.start.action.text.2"))
      .replaceFirst("%s", shortenNameIfNeeded(configurationName)).toString();
  }

  @Override
  public @NotNull String getToolWindowId() {
    return ToolWindowId.RUN;
  }

  @Override
  public @NotNull Icon getToolWindowIcon() {
    return AllIcons.Toolwindows.ToolWindowRun;
  }

  @Override
  public @NotNull Icon getIcon() {
    return AllIcons.Actions.Execute;
  }

  @Override
  public @NotNull Icon getRerunIcon() {
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
  public @NotNull String getActionName() {
    return ExecutionBundle.message("tool.window.name.run");
  }

  @Override
  public @NotNull String getId() {
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
