package com.intellij.execution.ui;

import com.intellij.execution.ExecutionResult;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class ExecutionUiService {
  public void assertTimeConsuming() {
  }

  public RunContentDescriptor showRunContent(@NotNull ExecutionResult executionResult,
                                             @NotNull ExecutionEnvironment environment) {
    return null;
  }

  public static ExecutionUiService getInstance() {
    return ApplicationManager.getApplication().getService(ExecutionUiService.class);
  }
}
