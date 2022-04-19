// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.target.value.TargetValue;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

public class TargetedCommandLineBuilder extends UserDataHolderBase {
  @NotNull private TargetValue<String> myExePath = TargetValue.empty();
  @NotNull private TargetValue<String> myWorkingDirectory = TargetValue.empty();
  @NotNull private TargetValue<String> myInputFilePath = TargetValue.empty();
  @NotNull private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();

  @NotNull private final List<TargetValue<String>> myParameters = new ArrayList<>();
  @NotNull private final Map<String, TargetValue<String>> myEnvironment = new HashMap<>();
  @NotNull private final Set<File> myFilesToDeleteOnTermination = new HashSet<>();

  @NotNull private final TargetEnvironmentRequest myRequest;
  private boolean myRedirectErrorStream = false;

  public TargetedCommandLineBuilder(@NotNull TargetEnvironmentRequest request) {
    myRequest = request;
  }

  @NotNull
  public TargetEnvironmentRequest getRequest() {
    return myRequest;
  }

  @NotNull
  public TargetedCommandLine build() {
    return new TargetedCommandLine(myExePath,
                                   myWorkingDirectory,
                                   myInputFilePath,
                                   myCharset,
                                   new ArrayList<>(myParameters),
                                   new HashMap<>(myEnvironment),
                                   myRedirectErrorStream);
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

  public @NotNull TargetValue<String> getExePath() {
    return myExePath;
  }

  public void setWorkingDirectory(@NotNull TargetValue<String> workingDirectory) {
    myWorkingDirectory = workingDirectory;
  }

  public void setWorkingDirectory(@NotNull String workingDirectory) {
    myWorkingDirectory = TargetValue.fixed(workingDirectory);
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

  public void addParameters(String @NotNull ... parametersList) {
    for (String parameter : parametersList) {
      addParameter(parameter);
    }
  }

  public void addParameterAt(int index, @NotNull String parameter) {
    addParameterAt(index, TargetValue.fixed(parameter));
  }

  public void addParameterAt(int index, @NotNull TargetValue<String> parameter) {
    myParameters.add(index, parameter);
  }

  public void addFixedParametersAt(int index, @NotNull List<String> parameters) {
    addParametersAt(index, ContainerUtil.map(parameters, p -> TargetValue.fixed(p)));
  }

  public void addParametersAt(int index, @NotNull List<TargetValue<String>> parameters) {
    int i = 0;
    for (TargetValue<String> parameter : parameters) {
      addParameterAt(index + i++, parameter);
    }
  }

  public void addEnvironmentVariable(@NotNull String name, @Nullable TargetValue<String> value) {
    if (value != null) {
      myEnvironment.put(name, value);
    }
    else {
      myEnvironment.remove(name);
    }
  }

  public void addEnvironmentVariable(@NotNull String name, @Nullable String value) {
    addEnvironmentVariable(name, value != null ? TargetValue.fixed(value) : null);
  }

  public void removeEnvironmentVariable(@NotNull String name) {
    myEnvironment.remove(name);
  }

  public @Nullable TargetValue<String> getEnvironmentVariable(@NotNull String name) {
    return myEnvironment.get(name);
  }

  public void addFileToDeleteOnTermination(@NotNull File file) {
    myFilesToDeleteOnTermination.add(file);
  }

  public void setInputFile(@NotNull TargetValue<String> inputFilePath) {
    myInputFilePath = inputFilePath;
  }

  @NotNull
  public Set<File> getFilesToDeleteOnTermination() {
    return myFilesToDeleteOnTermination;
  }

  public void setRedirectErrorStreamFromRegistry() {
    setRedirectErrorStream(Registry.is("run.processes.with.redirectedErrorStream", false));
  }

  public void setRedirectErrorStream(boolean redirectErrorStream) {
    myRedirectErrorStream = redirectErrorStream;
  }
}
