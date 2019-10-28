// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.debugger.impl.RemoteConnectionBuilder;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.*;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.BaseOutputReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Optional;

/**
 * @author nik
 */
public abstract class BaseJavaApplicationCommandLineState<T extends RunConfigurationBase & CommonJavaRunConfigurationParameters>
  extends JavaCommandLineState implements RemoteConnectionCreator {

  @NotNull protected final T myConfiguration;

  @Nullable private TargetEnvironmentFactory myRemoteRunner;
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
      TargetEnvironmentFactory runner = getRemoteRunner(environment);
      if (!(runner instanceof LocalTargetEnvironmentFactory)) {
        final String remotePort = "12345";
        final String remoteAddressForVmParams;

        final boolean java9plus = Optional.ofNullable(runner.getTargetConfiguration())
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

        myRemoteConnection.setApplicationPort(remotePort);
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
    TargetEnvironmentFactory runner = getRemoteRunner(getEnvironment());
    TargetEnvironmentRequest request = runner.createRequest();
    if (myRemoteConnection != null) {
      final int remotePort = StringUtil.parseInt(myRemoteConnection.getApplicationPort(), -1);
      if (remotePort > 0) {
        request.bindTargetPort(remotePort).promise().onSuccess(it -> {
          myRemoteConnection.setDebuggerHostName("0.0.0.0");
          myRemoteConnection.setDebuggerPort(String.valueOf(it.getLocalValue()));
        });
      }
    }

    TargetedCommandLine targetedCommandLine = createNewCommandLine(request, runner.getTargetConfiguration());

    File inputFile = InputRedirectAware.getInputFile(myConfiguration);
    if (inputFile != null) {
      targetedCommandLine.setInputFile(request.createUpload(inputFile.getAbsolutePath()));
    }

    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    TargetEnvironment remoteEnvironment = runner.prepareRemoteEnvironment(request, indicator);
    Process process = remoteEnvironment.createProcess(targetedCommandLine, indicator);

    //todo[remoteServers]: invent the new method for building presentation string
    String commandRepresentation = StringUtil.join(targetedCommandLine.prepareCommandLine(remoteEnvironment), " ");

    OSProcessHandler handler = new KillableColoredProcessHandler.Silent(process, commandRepresentation, targetedCommandLine.getCharset());
    ProcessTerminatedListener.attach(handler);
    JavaRunConfigurationExtensionManager.getInstance().attachExtensionsToProcess(getConfiguration(), handler, getRunnerSettings());
    return handler;
  }

  @NotNull
  private TargetEnvironmentFactory getRemoteRunner(@NotNull ExecutionEnvironment environment)
    throws ExecutionException {
    if (myRemoteRunner != null) {
      return myRemoteRunner;
    }
    if (myConfiguration instanceof TargetEnvironmentAwareRunProfile && Experiments.getInstance().isFeatureEnabled("runtime.environments")) {
      String targetName = ((TargetEnvironmentAwareRunProfile)myConfiguration).getDefaultTargetName();
      if (targetName != null) {
        TargetEnvironmentConfiguration config = RemoteTargetsManager.getInstance().getTargets().findByName(targetName);
        if (config == null) {
          throw new ExecutionException("Cannot find target " + targetName);
        }
        return myRemoteRunner = config.createRunner(environment.getProject());
      }
    }
    return myRemoteRunner = new LocalTargetEnvironmentFactory();
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