// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.*;
import com.intellij.execution.target.java.JavaLanguageRuntimeConfiguration;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory;
import com.intellij.execution.wsl.WslPath;
import com.intellij.execution.wsl.target.WslTargetEnvironmentConfiguration;
import com.intellij.execution.wsl.target.WslTargetEnvironmentFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class JavaCommandLineState extends CommandLineState implements JavaCommandLine, TargetEnvironmentAwareRunProfileState, RemoteConnectionCreator {
  private static final Logger LOG = Logger.getInstance(JavaCommandLineState.class);
  private JavaParameters myParams;
  private TargetEnvironmentRequest myTargetEnvironmentRequest;
  private TargetedCommandLineBuilder myCommandLine;
  @Nullable private volatile TargetDebuggerConnection myTargetDebuggerConnection;

  protected JavaCommandLineState(@NotNull ExecutionEnvironment environment) {
    super(environment);
  }

  @Override
  public JavaParameters getJavaParameters() throws ExecutionException {
    if (myParams == null) {
      myParams = ReadAction.compute(this::createJavaParameters);
    }
    return myParams;
  }

  public void clear() {
    myParams = null;
  }

  @Override
  @NotNull
  protected OSProcessHandler startProcess() throws ExecutionException {
    return JavaCommandLineStateUtil.startProcess(createCommandLine(), ansiColoringEnabled());
  }

  protected boolean ansiColoringEnabled() {
    return true;
  }

  protected abstract JavaParameters createJavaParameters() throws ExecutionException;

  @Override
  public TargetEnvironmentFactory createCustomTargetEnvironmentFactory() {
    try {
      JavaParameters parameters = getJavaParameters();
      WslTargetEnvironmentConfiguration config = checkCreateWslConfiguration(parameters);
      return config == null ? null : new WslTargetEnvironmentFactory(config);
    }
    catch (ExecutionException e) {
      // ignore
    }
    return null;
  }

  protected static WslTargetEnvironmentConfiguration checkCreateWslConfiguration(JavaParameters parameters) {
    String path;
    try {
      path = parameters.getJdkPath();
    }
    catch (CantRunException e) {
      return null;
    }
    WslPath wslPath = WslPath.parseWindowsUncPath(path);
    Sdk jdk = parameters.getJdk();
    if (jdk != null && wslPath != null) {
      WslTargetEnvironmentConfiguration config = new WslTargetEnvironmentConfiguration(wslPath.getDistribution());
      JavaLanguageRuntimeConfiguration javaConfig = new JavaLanguageRuntimeConfiguration();
      javaConfig.setHomePath(wslPath.getLinuxPath());
      String jdkVersionString = jdk.getVersionString();
      if (jdkVersionString != null) {
        javaConfig.setJavaVersionString(jdkVersionString);
      }
      config.addLanguageRuntime(javaConfig);
      return config;
    }
    return null;
  }

  protected boolean shouldPrepareDebuggerConnection() {
    return true;
  }

  @Override
  public void prepareTargetEnvironmentRequest(
    @NotNull TargetEnvironmentRequest request,
    @Nullable TargetEnvironmentConfiguration configuration,
    @NotNull TargetProgressIndicator targetProgressIndicator) throws ExecutionException {
    targetProgressIndicator.addSystemLine(ExecutionBundle.message("progress.text.prepare.target.requirements"));

    myTargetEnvironmentRequest = request;

    TargetDebuggerConnection targetDebuggerConnection =
      shouldPrepareDebuggerConnection() ? TargetDebuggerConnectionUtil.prepareDebuggerConnection(this, request, configuration) : null;
    myTargetDebuggerConnection = targetDebuggerConnection;

    Ref<TargetedCommandLineBuilder> commandLineRef = new Ref<>();
    Ref<ExecutionException> exceptionRef = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        commandLineRef.set(createTargetedCommandLine(myTargetEnvironmentRequest, configuration));
      }
      catch (ExecutionException e) {
        exceptionRef.set(e);
      }
    });
    if(!exceptionRef.isNull()){
      throw exceptionRef.get();
    }
    myCommandLine = commandLineRef.get();

    if (targetDebuggerConnection != null) {
      Objects.requireNonNull(request).getTargetPortBindings().add(targetDebuggerConnection.getDebuggerPortRequest());
    }
  }

  @Override
  public void handleCreatedTargetEnvironment(@NotNull TargetEnvironment environment,
                                             @NotNull TargetProgressIndicator targetProgressIndicator) {
    TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
    Objects.requireNonNull(targetedCommandLineBuilder.getUserData(JdkUtil.COMMAND_LINE_SETUP_KEY))
      .provideEnvironment(environment, targetProgressIndicator);

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

  public synchronized @Nullable TargetEnvironmentRequest getTargetEnvironmentRequest() {
    return myTargetEnvironmentRequest;
  }

  @NotNull
  protected synchronized TargetedCommandLineBuilder getTargetedCommandLine() {
    if (myCommandLine != null) {
      // In a correct implementation that uses the new API this condition is always true.
      return myCommandLine;
    }

    if (Experiments.getInstance().isFeatureEnabled("run.targets") &&
        !(getEnvironment().getTargetEnvironmentFactory() instanceof LocalTargetEnvironmentFactory)) {
      LOG.error("Command line hasn't been built yet. " +
                "Probably you need to run environment#getPreparedTargetEnvironment first, " +
                "or it return the environment from the previous run session");
    }
    try {
      // force re-prepareTargetEnvironment in order to drop previous environment
      getEnvironment().prepareTargetEnvironment(this, TargetProgressIndicator.EMPTY);
      return myCommandLine;
    }
    catch (ExecutionException e) {
      LOG.error(e);
      throw new RuntimeException(e);
    }
  }

  @NotNull
  protected TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request,
                                                                 @Nullable TargetEnvironmentConfiguration configuration)
    throws ExecutionException {
    SimpleJavaParameters javaParameters = getJavaParameters();
    if (!javaParameters.isDynamicClasspath()) {
      javaParameters.setUseDynamicClasspath(getEnvironment().getProject());
    }
    return javaParameters.toCommandLine(request, configuration);
  }

  protected GeneralCommandLine createCommandLine() throws ExecutionException {
    LocalTargetEnvironmentFactory factory = new LocalTargetEnvironmentFactory();
    boolean redirectErrorStream = Registry.is("run.processes.with.redirectedErrorStream", false);
    TargetEnvironmentRequest request = factory.createRequest();
    TargetedCommandLineBuilder targetedCommandLineBuilder = createTargetedCommandLine(request, factory.getTargetConfiguration());
    LocalTargetEnvironment environment = factory.prepareRemoteEnvironment(request, TargetProgressIndicator.EMPTY);
    Objects.requireNonNull(targetedCommandLineBuilder.getUserData(JdkUtil.COMMAND_LINE_SETUP_KEY))
      .provideEnvironment(environment, TargetProgressIndicator.EMPTY);
    return environment
      .createGeneralCommandLine(targetedCommandLineBuilder.build())
      .withRedirectErrorStream(redirectErrorStream);
  }

  public boolean shouldAddJavaProgramRunnerActions() {
    return true;
  }
}