// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetedCommandLineBuilder;
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JavaCommandLineState extends CommandLineState implements JavaCommandLine {
  private JavaParameters myParams;

  protected JavaCommandLineState(@NotNull ExecutionEnvironment environment) {
    super(environment);
  }

  @Override
  public JavaParameters getJavaParameters() throws ExecutionException {
    if (myParams == null) {
      myParams = createJavaParameters();
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

  @NotNull
  protected TargetedCommandLineBuilder createTargetedCommandLine(@NotNull TargetEnvironmentRequest request,
                                                                 @Nullable TargetEnvironmentConfiguration configuration) throws ExecutionException {
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
    return factory.prepareRemoteEnvironment(request, new EmptyProgressIndicator())
      .createGeneralCommandLine(targetedCommandLineBuilder.build())
      .withRedirectErrorStream(redirectErrorStream);
  }

  public boolean shouldAddJavaProgramRunnerActions() {
    return true;
  }
}