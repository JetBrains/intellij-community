package com.intellij.execution;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public abstract class ExecutorRegistry implements ApplicationComponent {

  public static ExecutorRegistry getInstance() {
    return ApplicationManager.getApplication().getComponent(ExecutorRegistry.class);
  }

  @NotNull
  public abstract Executor[] getRegisteredExecutors();

  public abstract Executor getExecutorById(final String executorId);
}
