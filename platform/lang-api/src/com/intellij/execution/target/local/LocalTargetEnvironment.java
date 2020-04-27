// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target.local;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetPlatform;
import com.intellij.execution.target.TargetedCommandLine;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class LocalTargetEnvironment implements TargetEnvironment {
  @NotNull
  private final TargetEnvironmentRequest myRequest;

  public LocalTargetEnvironment(@NotNull TargetEnvironmentRequest request) {
    myRequest = request;
  }

  @NotNull
  @Override
  public TargetEnvironmentRequest getRequest() {
    return myRequest;
  }

  @NotNull
  @Override
  public TargetPlatform getRemotePlatform() {
    return TargetPlatform.CURRENT;
  }

  @NotNull
  @Override
  public Process createProcess(@NotNull TargetedCommandLine commandLine, @NotNull ProgressIndicator indicator) throws ExecutionException {
    return createGeneralCommandLine(commandLine).createProcess();
  }

  @NotNull
  public GeneralCommandLine createGeneralCommandLine(@NotNull TargetedCommandLine commandLine) throws CantRunException {
    try {
      GeneralCommandLine generalCommandLine = new GeneralCommandLine(commandLine.collectCommandsSynchronously());
      if (myRequest instanceof LocalTargetEnvironmentRequest) {
        generalCommandLine.withParentEnvironmentType(((LocalTargetEnvironmentRequest)myRequest).getParentEnvironmentType());
      }
      String inputFilePath = commandLine.getInputFilePath();
      if (inputFilePath != null) {
        generalCommandLine.withInput(new File(inputFilePath));
      }
      generalCommandLine.withCharset(commandLine.getCharset());
      String workingDirectory = commandLine.getWorkingDirectory();
      if (workingDirectory != null) {
        generalCommandLine.withWorkDirectory(workingDirectory);
      }
      generalCommandLine.withEnvironment(commandLine.getEnvironmentVariables());
      return generalCommandLine;
    }
    catch (ExecutionException e) {
      throw new CantRunException(e.getMessage(), e);
    }
  }
}

