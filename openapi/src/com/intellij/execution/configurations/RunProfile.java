/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.runners.RunnerInfo;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;

public interface RunProfile {
  /**
   * todo - javadoc
   *
   * @param context
   * @param runnerInfo
   * @param runnerSettings
   * @param configurationSettings
   * @return
   */
  RunProfileState getState(DataContext context,
                           RunnerInfo runnerInfo,
                           RunnerSettings runnerSettings,
                           ConfigurationPerRunnerSettings configurationSettings) throws ExecutionException;

  String getName();

  void checkConfiguration() throws RuntimeConfigurationException;

  // return modules to compile before run. Null or empty list to make project
  Module[] getModules();
}
