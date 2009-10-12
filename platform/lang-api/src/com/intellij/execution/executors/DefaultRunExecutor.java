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
import com.intellij.openapi.util.IconLoader;
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

  private static final Icon ICON = IconLoader.getIcon("/actions/execute.png");
  private static final Icon TOOLWINDOW_ICON = IconLoader.getIcon("/general/toolWindowRun.png");
  private static final Icon DISABLED_ICON = IconLoader.getIcon("/process/disabledRun.png");

  @NotNull
  public String getStartActionText() {
    return ExecutionBundle.message("default.runner.start.action.text");
  }

  public String getToolWindowId() {
    return ToolWindowId.RUN;
  }

  public Icon getToolWindowIcon() {
    return TOOLWINDOW_ICON;
  }

  @NotNull
  public Icon getIcon() {
    return ICON;
  }

  public Icon getDisabledIcon() {
    return DISABLED_ICON;
  }

  public String getDescription() {
    return ExecutionBundle.message("standard.runner.description");
  }

  @NotNull
  public String getActionName() {
    return UIBundle.message("tool.window.name.run");
  }

  @NotNull
  public String getId() {
    return EXECUTOR_ID;
  }

  public String getContextActionId() {
    return "RunClass";
  }

  public String getHelpId() {
    return "ideaInterface.run";
  }

  public static Executor getRunExecutorInstance() {
    return ExecutorRegistry.getInstance().getExecutorById(EXECUTOR_ID);
  }
}
