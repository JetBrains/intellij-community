/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.*;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.util.JDOMExternalizable;

public interface JavaProgramRunner<Settings extends JDOMExternalizable> {
  Settings createConfigurationData(ConfigurationInfoProvider settingsProvider);

  void patch(JavaParameters javaParameters, RunnerSettings settings, final boolean beforeExecution) throws ExecutionException;

  void checkConfiguration(RunnerSettings settings, ConfigurationPerRunnerSettings configurationPerRunnerSettings) throws RuntimeConfigurationException;

  void onProcessStarted(RunnerSettings settings, ExecutionResult executionResult);

  AnAction[] createActions(ExecutionResult executionResult);

  RunnerInfo getInfo();

  SettingsEditor<Settings> getSettingsEditor(RunConfiguration configuration);
}