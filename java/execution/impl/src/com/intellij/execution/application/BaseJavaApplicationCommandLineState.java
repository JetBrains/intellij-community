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
import com.intellij.execution.remote.IR;
import com.intellij.execution.remote.target.IRExecutionTarget;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author nik
 */
public abstract class BaseJavaApplicationCommandLineState<T extends RunConfigurationBase & CommonJavaRunConfigurationParameters>
  extends JavaCommandLineState {
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
    //todo[remoteServers]: pull up and support all implementations of JavaCommandLineState
    ExecutionEnvironment environment = getEnvironment();
    ExecutionTarget target = environment.getExecutionTarget();
    IR.RemoteRunner runner = target instanceof IRExecutionTarget ? ((IRExecutionTarget)target).getRemoteRunner() : new IR.LocalRunner();
    IR.RemoteEnvironmentRequest request = runner.createRequest();
    IR.NewCommandLine newCommandLine = createNewCommandLine(request);

    File inputFile = InputRedirectAware.getInputFile(myConfiguration);
    if (inputFile != null) {
      newCommandLine.setInputFile(request.createUpload(inputFile.getAbsolutePath()));
    }

    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    IR.RemoteEnvironment remoteEnvironment = runner.prepareRemoteEnvironment(request, indicator);
    Process process = remoteEnvironment.createProcess(newCommandLine, indicator);

    //todo[remoteServers]: invent the new method for building presentation string
    String commandRepresentation = StringUtil.join(newCommandLine.prepareCommandLine(remoteEnvironment), " ");

    OSProcessHandler handler = new KillableColoredProcessHandler.Silent(process, commandRepresentation, newCommandLine.getCharset());
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