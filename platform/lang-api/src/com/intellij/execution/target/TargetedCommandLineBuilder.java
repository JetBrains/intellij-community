// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.target.value.TargetValue;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;

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
                                   new HashMap<>(myEnvironment));
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

  @NotNull
  public Set<File> getFilesToDeleteOnTermination() {
    return myFilesToDeleteOnTermination;
  }
}
