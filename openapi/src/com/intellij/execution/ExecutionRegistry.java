/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution;

import com.intellij.execution.runners.JavaProgramRunner;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;

public abstract class ExecutionRegistry implements ApplicationComponent {
  public static ExecutionRegistry getInstance() {
    return ApplicationManager.getApplication().getComponent(ExecutionRegistry.class);
  }

  public abstract void registerRunner(JavaProgramRunner runner);

  public abstract void unregisterRunner(JavaProgramRunner runner);

  public abstract JavaProgramRunner getDefaultRunner();

  public abstract JavaProgramRunner getDebuggerRunner();

  public abstract JavaProgramRunner[] getRegisteredRunners();

  public abstract JavaProgramRunner findRunnerById(String id);
}
