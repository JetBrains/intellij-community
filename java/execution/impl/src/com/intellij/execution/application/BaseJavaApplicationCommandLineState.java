// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.*;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class BaseJavaApplicationCommandLineState<T extends RunConfigurationBase&CommonJavaRunConfigurationParameters> extends JavaCommandLineState {
  @NotNull protected final T myConfiguration;

  public BaseJavaApplicationCommandLineState(ExecutionEnvironment environment, @NotNull final T configuration) {
    super(environment);
    myConfiguration = configuration;
  }

  protected void setupJavaParameters(@NotNull JavaParameters params) throws ExecutionException {
    JavaParametersUtil.configureConfiguration(params, myConfiguration);

    for (RunConfigurationExtension ext : RunConfigurationExtension.EP_NAME.getExtensionList()) {
      ext.updateJavaParameters(getConfiguration(), params, getRunnerSettings(), getEnvironment().getExecutor());
    }
  }

  @NotNull
  @Override
  protected OSProcessHandler startProcess() throws ExecutionException {
    OSProcessHandler handler = new KillableColoredProcessHandler(createCommandLine()) {
      @NotNull
      @Override
      protected BaseOutputReader.Options readerOptions() {
        return BaseOutputReader.Options.forMostlySilentProcess();
      }
    };
    ProcessTerminatedListener.attach(handler);
    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(getConfiguration(), handler, getRunnerSettings());
    return handler;
  }

  @Override
  protected GeneralCommandLine createCommandLine() throws ExecutionException {
    return super.createCommandLine().withInput(InputRedirectAware.getInputFile(myConfiguration));
  }

  @NotNull
  protected T getConfiguration() {
    return myConfiguration;
  }
}
