// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.debugger.impl.RemoteConnectionBuilder;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.*;
import com.intellij.execution.configuration.RemoteTargetAwareRunProfile;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.remote.IR;
import com.intellij.execution.remote.RemoteTargetConfiguration;
import com.intellij.execution.remote.RemoteTargetsManager;
import com.intellij.execution.remote.target.IRExecutionTarget;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author nik
 */
public abstract class BaseJavaApplicationCommandLineState<T extends RunConfigurationBase & CommonJavaRunConfigurationParameters>
  extends JavaCommandLineState implements RemoteConnectionCreator {
  public static final int PORT = 12345;
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

  @Nullable
  @Override
  public RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
    try {
      return new RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, String.valueOf(PORT)).create(getJavaParameters());
    }
    catch (ExecutionException e) {
      e.printStackTrace();
    }
    return null;
  }

  @Override
  public boolean isPollConnection() {
    return true;
  }

  @NotNull
  @Override
  protected OSProcessHandler startProcess() throws ExecutionException {
    //todo[remoteServers]: pull up and support all implementations of JavaCommandLineState
    IR.RemoteRunner runner = getRemoteRunner(myConfiguration.getProject(), getEnvironment(), myConfiguration);
    IR.RemoteEnvironmentRequest request = runner.createRequest();
    IR.RemoteValue<Integer> port = null;
    if (!(runner instanceof IR.LocalRunner)) {
      port = request.bindRemotePort(PORT);
    }
    IR.NewCommandLine newCommandLine = createNewCommandLine(request, runner.getTargetConfiguration());

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
    if (port != null) {
      handler.putUserData(GenericDebuggerRunner.REMOTE_PORT_KEY, port.getLocalValue());
    }
    return handler;
  }

  @NotNull
  private static IR.RemoteRunner getRemoteRunner(@NotNull Project project,
                                                 @NotNull ExecutionEnvironment environment,
                                                 @NotNull RunProfile runConfiguration) throws ExecutionException {
    ExecutionTarget target = environment.getExecutionTarget();
    if (target instanceof IRExecutionTarget) {
      return ((IRExecutionTarget)target).createRemoteRunner();
    }
    if (runConfiguration instanceof RemoteTargetAwareRunProfile) {
      String targetName = ((RemoteTargetAwareRunProfile)runConfiguration).getDefaultTargetName();
      if (targetName != null) {
        RemoteTargetConfiguration config = RemoteTargetsManager.getInstance().getTargets().findByName(targetName);
        if (config == null) {
          throw new ExecutionException("Cannot find target " + targetName);
        }
        return config.createRunner(project);
      }
    }
    return new IR.LocalRunner();
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