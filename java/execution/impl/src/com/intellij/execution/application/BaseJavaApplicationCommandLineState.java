// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.debugger.impl.RemoteConnectionBuilder;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.ArgumentFileFilter;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.*;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.progress.EmptyProgressIndicator;
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

  @Nullable private RemoteConnection myRemoteConnection;

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
    //todo[remoteServers]: pull up and support all implementations of JavaCommandLineState
    try {
      TargetEnvironmentFactory environmentFactory = environment.getTargetEnvironmentFactory();
      if (!(environmentFactory instanceof LocalTargetEnvironmentFactory)) {
        final String remotePort = "12345";
        final String remoteAddressForVmParams;

        final boolean java9plus = Optional.ofNullable(environmentFactory.getTargetConfiguration())
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
          remoteAddressForVmParams = remotePort;
        }

        myRemoteConnection = new RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, remoteAddressForVmParams)
          .suspend(true)
          .create(getJavaParameters());

        myRemoteConnection.setApplicationAddress(remotePort);
        if (java9plus) {
          myRemoteConnection.setApplicationHostName("*");
        }

        return myRemoteConnection;
      }
    }
    catch (ExecutionException e) {
      return null;
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
    TargetEnvironment remoteEnvironment = getEnvironment().getPreparedTargetEnvironment(new EmptyProgressIndicator());
    TargetEnvironmentRequest request = remoteEnvironment.getRequest();
    if (myRemoteConnection != null) {
      final int remotePort = StringUtil.parseInt(myRemoteConnection.getApplicationAddress(), -1);
      if (remotePort > 0) {
        request.bindTargetPort(remotePort).getLocalValue().onSuccess(it -> {
          myRemoteConnection.setDebuggerHostName("0.0.0.0");
          myRemoteConnection.setDebuggerAddress(String.valueOf(it));
        });
      }
    }

    TargetedCommandLineBuilder targetedCommandLineBuilder = createTargetedCommandLine(request, getEnvironment().getTargetEnvironmentFactory().getTargetConfiguration());
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
  @Override
  protected TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request,
                                                                 @Nullable TargetEnvironmentConfiguration configuration)
    throws ExecutionException {
    TargetedCommandLineBuilder line = super.createTargetedCommandLine(request, configuration);
    File inputFile = InputRedirectAware.getInputFile(myConfiguration);
    if (inputFile != null) {
      line.setInputFile(request.createUpload(inputFile.getAbsolutePath()));
    }
    return line;
  }

  @NotNull
  protected T getConfiguration() {
    return myConfiguration;
  }
}