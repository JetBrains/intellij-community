/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.execution.ExecutionException;


public abstract class JavaCommandLineState extends CommandLineState implements JavaCommandLine{
  private JavaParameters myParams;
  
  protected JavaCommandLineState(RunnerSettings runnerSettings, ConfigurationPerRunnerSettings configurationSettings) {
    super(runnerSettings, configurationSettings);
  }

  public JavaParameters getJavaParameters() throws ExecutionException {
    if (myParams == null) {
      myParams = createJavaParameters();
    }
    return myParams;
  }

  protected abstract JavaParameters createJavaParameters() throws ExecutionException;

  protected GeneralCommandLine createCommandLine() throws ExecutionException {
    return GeneralCommandLine.createFromJavaParameters(getJavaParameters());
  }

}
