// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.value.TargetValue;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Command line that can be executed on any {@link TargetEnvironment}.
 * <p>
 * Exe-path, working-directory and other properties are initialized in a lazy way,
 * that allows to create a command line before creating a target where it should be run.
 */
public class TargetedCommandLine extends UserDataHolderBase {
  private TargetValue<String> myExePath = TargetValue.empty();
  private TargetValue<String> myWorkingDirectory = TargetValue.empty();
  private TargetValue<String> myInputFilePath = TargetValue.empty();
  private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();

  private final List<TargetValue<String>> myParameters = new ArrayList<>();
  private final Map<String, TargetValue<String>> myEnvironment = new HashMap<>();
  private final Set<File> myFilesToDeleteOnTermination = new HashSet<>();

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

  public void setCharset(@NotNull Charset charset) {
    myCharset = charset;
  }

  public void setExePath(@NotNull TargetValue<String> exePath) {
    myExePath = exePath;
  }

  public void setExePath(@NotNull String exePath) {
    myExePath = TargetValue.fixed(exePath);
  }

  public void setWorkingDirectory(@NotNull TargetValue<String> workingDirectory) {
    myWorkingDirectory = workingDirectory;
  }

  public void addParameter(@NotNull TargetValue<String> parameter) {
    myParameters.add(parameter);
  }

  public void addParameter(@NotNull String parameter) {
    myParameters.add(TargetValue.fixed(parameter));
  }

  public void addParameters(@NotNull List<String> parametersList) {
    for (String parameter : parametersList) {
      addParameter(parameter);
    }
  }

  public void addEnvironmentVariable(String name, TargetValue<String> value) {
    myEnvironment.put(name, value);
  }

  public void addEnvironmentVariable(String name, String value) {
    myEnvironment.put(name, TargetValue.fixed(value));
  }

  public void addFileToDeleteOnTermination(@NotNull File file) {
    myFilesToDeleteOnTermination.add(file);
  }

  public void setInputFile(@NotNull TargetValue<String> inputFilePath) {
    myInputFilePath = inputFilePath;
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

  @NotNull
  public Set<File> getFilesToDeleteOnTermination() {
    return myFilesToDeleteOnTermination;
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
