// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.execution.*;
import com.intellij.execution.configurations.JavaCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.projectRoots.JdkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;

public abstract class BaseJavaApplicationCommandLineState<T extends RunConfigurationBase & CommonJavaRunConfigurationParameters>
  extends JavaCommandLineState {

  @NotNull protected final T myConfiguration;

  public BaseJavaApplicationCommandLineState(ExecutionEnvironment environment, @NotNull final T configuration) {
    super(environment);
    myConfiguration = configuration;
  }

  protected void setupJavaParameters(@NotNull JavaParameters params) throws ExecutionException {
    ReadAction.run(() -> JavaRunConfigurationExtensionManager.getInstance()
      .updateJavaParameters(getConfiguration(), params, getRunnerSettings(), getEnvironment().getExecutor()));
  }

  @Override
  public void prepareTargetEnvironmentRequest(@NotNull TargetEnvironmentRequest request,
                                              @NotNull TargetProgressIndicator targetProgressIndicator) throws ExecutionException {
    if (myConfiguration.getProjectPathOnTarget() != null) {
      request.setProjectPathOnTarget(myConfiguration.getProjectPathOnTarget());
    }
    super.prepareTargetEnvironmentRequest(request, targetProgressIndicator);
  }

  @NotNull
  @Override
  protected TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request)
    throws ExecutionException {
    TargetedCommandLineBuilder line = super.createTargetedCommandLine(request);
    File inputFile = InputRedirectAware.getInputFile(myConfiguration);
    if (inputFile != null) {
      line.setInputFile(request.getDefaultVolume().createUpload(inputFile.getAbsolutePath()));
    }
    return line;
  }

  @Override
  protected @Nullable ConsoleView createConsole(@NotNull Executor executor) throws ExecutionException {
    ConsoleView console = super.createConsole(executor);
    if (console == null) {
      return null;
    }
    return JavaRunConfigurationExtensionManager.getInstance().decorateExecutionConsole(getConfiguration(), getRunnerSettings(), console, executor);
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