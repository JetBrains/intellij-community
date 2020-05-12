// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.debugger.impl.RemoteConnectionBuilder;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.*;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Optional;

public abstract class BaseJavaApplicationCommandLineState<T extends RunConfigurationBase & CommonJavaRunConfigurationParameters>
  extends JavaCommandLineState implements RemoteConnectionCreator {

  @NotNull protected final T myConfiguration;

  @Nullable private volatile RemoteConnection myRemoteConnection;

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
  public synchronized void prepareTargetEnvironmentRequest(@NotNull TargetEnvironmentRequest request,
                                                           @Nullable TargetEnvironmentConfiguration configuration,
                                                           @NotNull ProgressIndicator progressIndicator) throws ExecutionException {
    prepareRemoteConnection(request, configuration);
    super.prepareTargetEnvironmentRequest(request, configuration, progressIndicator);
  }

  private void prepareRemoteConnection(@NotNull TargetEnvironmentRequest request,
                                       @Nullable TargetEnvironmentConfiguration configuration) {
    //todo[remoteServers]: pull up and support all implementations of JavaCommandLineState
    if (!DefaultDebugExecutor.EXECUTOR_ID.equalsIgnoreCase(getEnvironment().getExecutor().getId())) {
      myRemoteConnection = null;
      return;
    }

    try {
      if (!(request instanceof LocalTargetEnvironmentRequest)) {
        final int remotePort = 12345;
        final String remoteAddressForVmParams;

        final boolean java9plus = Optional.ofNullable(configuration)
          .map(TargetEnvironmentConfiguration::getRuntimes)
          .map(list -> list.findByType(JavaLanguageRuntimeConfiguration.class))
          .map(JavaLanguageRuntimeConfiguration::getJavaVersionString)
          .filter(StringUtil::isNotEmpty)
          .map(JavaSdkVersion::fromVersionString)
          .map(v -> v.isAtLeast(JavaSdkVersion.JDK_1_9))
          .orElse(false);

        if (java9plus) {
          // IDEA-225182 - hack: pass "host:port" to construct correct VM params, then adjust the connection
          remoteAddressForVmParams = "*:" + remotePort;
        }
        else {
          remoteAddressForVmParams = String.valueOf(remotePort);
        }

        RemoteConnection remoteConnection = new RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, remoteAddressForVmParams)
          .suspend(true)
          .create(getJavaParameters());

        remoteConnection.setApplicationAddress(String.valueOf(remotePort));
        if (java9plus) {
          remoteConnection.setApplicationHostName("*");
        }

        request.bindTargetPort(remotePort).getLocalValue().onSuccess(it -> {
          remoteConnection.setDebuggerHostName("localhost");
          remoteConnection.setDebuggerAddress(String.valueOf(it));
        });
        myRemoteConnection = remoteConnection;
      }
    }
    catch (ExecutionException e) {
      myRemoteConnection = null;
    }
  }

  @Nullable
  @Override
  public RemoteConnection createRemoteConnection(ExecutionEnvironment environment) {
    return myRemoteConnection;
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
    TargetEnvironment remoteEnvironment = getEnvironment().getPreparedTargetEnvironment(this, new EmptyProgressIndicator());
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