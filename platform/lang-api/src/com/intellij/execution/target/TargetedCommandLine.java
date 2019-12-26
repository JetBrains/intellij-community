// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.value.TargetValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Command line that can be executed on any {@link TargetEnvironment}.
 * <p>
 * Exe-path, working-directory and other properties are initialized in a lazy way,
 * that allows to create a command line before creating a target where it should be run.
 *
 * @see TargetedCommandLineBuilder
 */
public class TargetedCommandLine {
  @NotNull private final TargetValue<String> myExePath;
  @NotNull private final TargetValue<String> myWorkingDirectory;
  @NotNull private final TargetValue<String> myInputFilePath;
  @NotNull private final Charset myCharset;
  @NotNull private final List<TargetValue<String>> myParameters;
  @NotNull private final Map<String, TargetValue<String>> myEnvironment;

  public TargetedCommandLine(@NotNull TargetValue<String> exePath,
                             @NotNull TargetValue<String> workingDirectory,
                             @NotNull TargetValue<String> inputFilePath,
                             @NotNull Charset charset,
                             @NotNull List<TargetValue<String>> parameters,
                             @NotNull Map<String, TargetValue<String>> environment) {
    myExePath = exePath;
    myWorkingDirectory = workingDirectory;
    myInputFilePath = inputFilePath;
    myCharset = charset;
    myParameters = parameters;
    myEnvironment = environment;
  }

  /**
   * {@link GeneralCommandLine#getPreparedCommandLine()}
   */
  public List<String> prepareCommandLine(@NotNull TargetEnvironment target) throws com.intellij.execution.ExecutionException {
    String command = resolvePromise(myExePath.getTargetValue(), "exe path");
    if (command == null) {
      throw new com.intellij.execution.ExecutionException("Resolved value for exe path is null");
    }
    List<String> parameters = new ArrayList<>();
    for (TargetValue<String> parameter : myParameters) {
      parameters.add(resolvePromise(parameter.getTargetValue(), "parameter"));
    }
    return CommandLineUtil.toCommandLine(command, parameters, target.getRemotePlatform().getPlatform());
  }

  @Nullable
  public String getWorkingDirectory() throws com.intellij.execution.ExecutionException {
    return resolvePromise(myWorkingDirectory.getTargetValue(), "working directory");
  }

  @Nullable
  public String getInputFilePath() throws com.intellij.execution.ExecutionException {
    return resolvePromise(myInputFilePath.getTargetValue(), "input file path");
  }

  @NotNull
  public Map<String, String> getEnvironmentVariables() throws com.intellij.execution.ExecutionException {
    Map<String, String> result = new LinkedHashMap<>();
    for (Map.Entry<String, TargetValue<String>> e : myEnvironment.entrySet()) {
      result.put(e.getKey(), resolvePromise(e.getValue().getTargetValue(), "environment variable " + e.getKey()));
    }
    return result;
  }

  @NotNull
  public Charset getCharset() {
    return myCharset;
  }

  @Nullable
  private static String resolvePromise(@NotNull Promise<String> promise, @NotNull String debugName)
    throws com.intellij.execution.ExecutionException {
    try {
      return promise.blockingGet(0);
    }
    catch (ExecutionException | TimeoutException e) {
      throw new com.intellij.execution.ExecutionException("Couldn't resolve promise for " + debugName, e);
    }
  }
}
