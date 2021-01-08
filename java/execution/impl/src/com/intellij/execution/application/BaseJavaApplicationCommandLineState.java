// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.*;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.projectRoots.JdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Objects;

public abstract class BaseJavaApplicationCommandLineState<T extends RunConfigurationBase & CommonJavaRunConfigurationParameters>
  extends JavaCommandLineState implements RemoteConnectionCreator {

  @NotNull protected final T myConfiguration;

  @Nullable private TargetDebuggerConnection myTargetDebuggerConnection;

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

  @Override
  public void prepareTargetEnvironmentRequest(@NotNull TargetEnvironmentRequest request,
                                              @Nullable TargetEnvironmentConfiguration configuration,
                                              @NotNull TargetProgressIndicator targetProgressIndicator) throws ExecutionException {
    TargetDebuggerConnection targetDebuggerConnection =
      TargetDebuggerConnectionUtil.prepareDebuggerConnection(this, request, configuration);
    myTargetDebuggerConnection = targetDebuggerConnection;
    super.prepareTargetEnvironmentRequest(request, configuration, targetProgressIndicator);
    if (targetDebuggerConnection != null) {
      Objects.requireNonNull(request).getTargetPortBindings().add(targetDebuggerConnection.getDebuggerPortRequest());
    }
  }

  @Override
  public void handleCreatedTargetEnvironment(@NotNull TargetEnvironment environment,
                                             @NotNull TargetProgressIndicator targetProgressIndicator) {
    // TODO Should the debugger initialization be moved into the super class?
    super.handleCreatedTargetEnvironment(environment, targetProgressIndicator);
    TargetDebuggerConnection targetDebuggerConnection = myTargetDebuggerConnection;
    if (targetDebuggerConnection != null) {
      targetDebuggerConnection.resolveRemoteConnection(environment);
    }
  }

  @Nullable
  @Override
  public RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
    TargetDebuggerConnection targetDebuggerConnection = myTargetDebuggerConnection;
    if (targetDebuggerConnection != null) {
      return targetDebuggerConnection.getResolvedRemoteConnection();
    }
    else {
      return null;
    }
  }

  @Override
  public boolean isPollConnection() {
    return true;
  }

  @NotNull
  @Override
  protected TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request,
                                                                 @Nullable TargetEnvironmentConfiguration configuration)
    throws ExecutionException {
    TargetedCommandLineBuilder line = super.createTargetedCommandLine(request, configuration);
    File inputFile = InputRedirectAware.getInputFile(myConfiguration);
    if (inputFile != null) {
      line.setInputFile(request.getDefaultVolume().createUpload(inputFile.getAbsolutePath()));
    }
    return line;
  }

  @NotNull
  @Override
  protected OSProcessHandler startProcess() throws ExecutionException {
    //todo[remoteServers]: pull up and support all implementations of JavaCommandLineState

    TargetEnvironment remoteEnvironment = getEnvironment().getPreparedTargetEnvironment(this, TargetProgressIndicator.EMPTY);
    TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
    TargetedCommandLine targetedCommandLine = targetedCommandLineBuilder.build();
    Process process = remoteEnvironment.createProcess(targetedCommandLine, new EmptyProgressIndicator());

    Map<String, String> content = targetedCommandLineBuilder.getUserData(JdkUtil.COMMAND_LINE_CONTENT);
    if (content != null) {
      content.forEach((key, value) -> addConsoleFilters(new ArgumentFileFilter(key, value)));
    }
    OSProcessHandler handler = new KillableColoredProcessHandler.Silent(process,
                                                                        targetedCommandLine.getCommandPresentation(remoteEnvironment),
                                                                        targetedCommandLine.getCharset(),
                                                                        targetedCommandLineBuilder.getFilesToDeleteOnTermination());
    ProcessTerminatedListener.attach(handler);
    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(getConfiguration(), handler, getRunnerSettings());
    return handler;
  }

  @NotNull
  protected T getConfiguration() {
    return myConfiguration;
  }
}