/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.executors;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.ui.UIBundle;
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

  @Override
  public String getToolWindowId() {
    return ToolWindowId.RUN;
  }

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
  public Icon getDisabledIcon() {
    return AllIcons.Process.DisabledRun;
  }

  @Override
  public String getDescription() {
    return ExecutionBundle.message("standard.runner.description");
  }

  @Override
  @NotNull
  public String getActionName() {
    return UIBundle.message("tool.window.name.run");
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

  public static Executor getRunExecutorInstance() {
    return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID);
  }
}
