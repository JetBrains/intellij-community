// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target;

import com.intellij.execution.CommandLineUtil;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.value.TargetValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.*;

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
  public List<String> prepareCommandLine(@NotNull TargetEnvironment target) {
    String command = getExePath();
    if (command == null) {
      // todo[remoteServers]: handle this properly
      throw new RuntimeException("Cannot find command");
    }
    return CommandLineUtil.toCommandLine(command, getParameters(), target.getRemotePlatform().getPlatform());
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

  public String getExePath() {
    return myExePath.getTargetValue();
  }

  @Nullable
  public String getWorkingDirectory() {
    return myWorkingDirectory.getTargetValue();
  }

  @Nullable
  public String getInputFilePath() {
    return myInputFilePath.getTargetValue();
  }

  @NotNull
  public Map<String, String> getEnvironmentVariables() {
    return ContainerUtil.map2MapNotNull(myEnvironment.entrySet(), e -> {
      String value = e.getValue().getTargetValue();
      return value != null ? Pair.create(e.getKey(), value) : null;
    });
  }

  @NotNull
  public Charset getCharset() {
    return myCharset;
  }

  @NotNull
  public Set<File> getFilesToDeleteOnTermination() {
    return myFilesToDeleteOnTermination;
  }

  @NotNull
  private List<String> getParameters() {
    return ContainerUtil.mapNotNull(myParameters, TargetValue::getTargetValue);
  }
}
