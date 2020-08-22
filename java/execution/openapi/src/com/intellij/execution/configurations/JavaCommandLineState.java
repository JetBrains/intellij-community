// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.*;
import com.intellij.execution.target.local.LocalTargetEnvironment;
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public abstract class JavaCommandLineState extends CommandLineState implements JavaCommandLine, TargetEnvironmentAwareRunProfileState {
  private static final Logger LOG = Logger.getInstance(JavaCommandLineState.class);
  private JavaParameters myParams;
  private TargetEnvironmentRequest myTargetEnvironmentRequest;
  private TargetedCommandLineBuilder myCommandLine;

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
  public void prepareTargetEnvironmentRequest(
    @NotNull TargetEnvironmentRequest request,
    @Nullable TargetEnvironmentConfiguration configuration,
    @NotNull ProgressIndicator progressIndicator
  ) throws ExecutionException {
    progressIndicator.setText(ExecutionBundle.message("progress.text.prepare.target.requirements"));
    myTargetEnvironmentRequest = request;
    myCommandLine = createTargetedCommandLine(myTargetEnvironmentRequest, configuration);
  }

  @Override
  public void handleCreatedTargetEnvironment(@NotNull TargetEnvironment environment, @NotNull ProgressIndicator progressIndicator) {
    TargetedCommandLineBuilder targetedCommandLineBuilder = getTargetedCommandLine();
    Objects.requireNonNull(targetedCommandLineBuilder.getUserData(JdkUtil.COMMAND_LINE_SETUP_KEY))
      .provideEnvironment(environment, progressIndicator);
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
      getEnvironment().prepareTargetEnvironment(this, new EmptyProgressIndicator());
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
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    LocalTargetEnvironment environment = factory.prepareRemoteEnvironment(request, indicator);
    Objects.requireNonNull(targetedCommandLineBuilder.getUserData(JdkUtil.COMMAND_LINE_SETUP_KEY))
      .provideEnvironment(environment, indicator);
    return environment
      .createGeneralCommandLine(targetedCommandLineBuilder.build())
      .withRedirectErrorStream(redirectErrorStream);
  }

  public boolean shouldAddJavaProgramRunnerActions() {
    return true;
  }
}