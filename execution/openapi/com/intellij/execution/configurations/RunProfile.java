/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
