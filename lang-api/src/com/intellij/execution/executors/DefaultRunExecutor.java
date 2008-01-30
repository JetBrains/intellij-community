package com.intellij.execution.executors;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.execution.Executor;
import com.intellij.execution.runners.GenericProgramRunner;
import com.intellij.ui.UIBundle;
import org.jetbrains.annotations.NonNls;

/**
 * @author spleaner
 */
public class DefaultRunExecutor extends Executor {
  @NonNls public static final String EXECUTOR_ID = ToolWindowId.RUN;

  public DefaultRunExecutor() {
    super(IconLoader.getIcon("/actions/execute.png"), EXECUTOR_ID, UIBundle.message("tool.window.name.run"), "RunClass");
  }

  public String getStartActionText() {
    return GenericProgramRunner.DEFAULT_RUNNER_INFO.getStartActionText();
  }
}
