// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.remote.IR;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;

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

  protected IR.NewCommandLine createNewCommandLine(@NotNull IR.RemoteRunner runner) throws ExecutionException {
    SimpleJavaParameters javaParameters = getJavaParameters();
    if (!javaParameters.isDynamicClasspath()) {
      javaParameters.setUseDynamicClasspath(getEnvironment().getProject());
    }
    return javaParameters.toCommandLine(runner);
  }

  protected GeneralCommandLine createCommandLine() throws ExecutionException {
    IR.LocalRunner runner = new IR.LocalRunner();
    boolean redirectErrorStream = Registry.is("run.processes.with.redirectedErrorStream", false);
    return runner.prepareRemoteEnvironment(runner.createRequest(), new EmptyProgressIndicator())
      .createGeneralCommandLine(createNewCommandLine(runner)).withRedirectErrorStream(redirectErrorStream);
  }

  public boolean shouldAddJavaProgramRunnerActions() {
    return true;
  }
}