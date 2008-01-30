package com.intellij.execution.executors;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.execution.Executor;
import com.intellij.ui.UIBundle;
import com.intellij.debugger.impl.GenericDebuggerRunner;
import org.jetbrains.annotations.NonNls;

/**
 * @author spleaner
 */
public class DefaultDebugExecutor extends Executor {
  @NonNls public static final String EXECUTOR_ID = ToolWindowId.DEBUG;

  public DefaultDebugExecutor() {
    super(IconLoader.getIcon("/actions/startDebugger.png"), EXECUTOR_ID, UIBundle.message("tool.window.name.debug"), "DebugClass");
  }

  public String getStartActionText() {
    return GenericDebuggerRunner.getRunnerInfo().getStartActionText();
  }
}
