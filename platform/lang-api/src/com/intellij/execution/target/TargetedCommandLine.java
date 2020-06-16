// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.value.TargetValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * Command line that can be executed on any {@link TargetEnvironment}.
 * <p>
 * Exe-path, working-directory and other properties are initialized in a lazy way,
 * that allows to create a command line before creating a target where it should be run.
 *
 * @see TargetedCommandLineBuilder
 */
public final class TargetedCommandLine {
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
  public String getCommandPresentation(@NotNull TargetEnvironment target) throws ExecutionException {
    String exePath = resolvePromise(myExePath.getTargetValue(), "exe path");
    if (exePath == null) {
      throw new ExecutionException("Resolved value for exe path is null");
    }
    List<String> parameters = new ArrayList<>();
    for (TargetValue<String> parameter : myParameters) {
      parameters.add(resolvePromise(parameter.getTargetValue(), "parameter"));
    }
    return StringUtil.join(CommandLineUtil.toCommandLine(ParametersListUtil.escape(exePath), parameters,
                                                         target.getTargetPlatform().getPlatform()), " ");
  }

  public List<String> collectCommandsSynchronously() throws ExecutionException {
    try {
      return collectCommands().blockingGet(0);
    }
    catch (java.util.concurrent.ExecutionException | TimeoutException e) {
      throw new ExecutionException("Couldn't collect commands", e);
    }
  }

  public @NotNull Promise<@NotNull List<@NotNull String>> collectCommands() {
    List<Promise<String>> promises = new ArrayList<>(myParameters.size() + 1);
    promises.add(myExePath.getTargetValue().then(command -> {
      if (command == null) {
        throw new IllegalStateException("Resolved value for exe path is null");
      }
      return command;
    }));
    for (TargetValue<String> parameter : myParameters) {
      promises.add(parameter.getTargetValue());
    }
    return Promises.collectResults(promises);
  }

  @Nullable
  public String getWorkingDirectory() throws ExecutionException {
    return resolvePromise(myWorkingDirectory.getTargetValue(), "working directory");
  }

  @Nullable
  public String getInputFilePath() throws ExecutionException {
    return resolvePromise(myInputFilePath.getTargetValue(), "input file path");
  }

  @NotNull
  public Map<String, String> getEnvironmentVariables() throws ExecutionException {
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
    throws ExecutionException {
    try {
      return promise.blockingGet(0);
    }
    catch (java.util.concurrent.ExecutionException | TimeoutException e) {
      throw new ExecutionException("Couldn't resolve promise for " + debugName, e);
    }
  }
}
