/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.openapi.module.Module;

public interface RunProfileState {
  ExecutionResult execute() throws ExecutionException;
  RunnerSettings getRunnerSettings();
  ConfigurationPerRunnerSettings getConfigurationSettings();
  Module[] getModulesToCompile();
}
