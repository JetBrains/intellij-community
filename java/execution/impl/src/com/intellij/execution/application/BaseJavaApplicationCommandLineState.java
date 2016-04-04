/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.execution.application;

import com.intellij.execution.CommonJavaRunConfigurationParameters;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class BaseJavaApplicationCommandLineState<T extends RunConfigurationBase&CommonJavaRunConfigurationParameters> extends JavaCommandLineState {
  protected final T myConfiguration;

  public BaseJavaApplicationCommandLineState(ExecutionEnvironment environment, @NotNull final T configuration) {
    super(environment);
    myConfiguration = configuration;
  }

  protected void setupJavaParameters(JavaParameters params) throws ExecutionException {
    JavaParametersUtil.configureConfiguration(params, myConfiguration);

    for (RunConfigurationExtension ext : RunConfigurationExtension.EP_NAME.getExtensions()) {
      ext.updateJavaParameters(getConfiguration(), params, getRunnerSettings());
    }
  }

  @NotNull
  @Override
  protected OSProcessHandler startProcess() throws ExecutionException {
    OSProcessHandler handler = new KillableColoredProcessHandler(createCommandLine());
    ProcessTerminatedListener.attach(handler);
    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(getConfiguration(), handler, getRunnerSettings());
    return handler;
  }

  protected T getConfiguration() {
    return myConfiguration;
  }
}
