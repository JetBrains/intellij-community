// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.util.EnvironmentUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SimpleProgramParameters {
  private final ParametersList myProgramParameters = new ParametersList();
  private String myWorkingDirectory;
  private Map<String, String> myEnv = new HashMap<>();
  private boolean myPassParentEnvs = true;

  public String getWorkingDirectory() {
    return myWorkingDirectory;
  }

  public void setWorkingDirectory(File path) {
    setWorkingDirectory(path.getPath());
  }

  public void setWorkingDirectory(String path) {
    myWorkingDirectory = path;
  }

  public ParametersList getProgramParametersList() {
    return myProgramParameters;
  }

  @NotNull
  public Map<String, String> getEnv() {
    return myEnv;
  }

  public String addEnv(String name, String value) {
    return myEnv.put(name, value);
  }

  public void setEnv(final Map<String, String> env) {
    myEnv = env;
  }

  public boolean isPassParentEnvs() {
    return myPassParentEnvs;
  }

  public void setPassParentEnvs(final boolean passDefaultEnvs) {
    myPassParentEnvs = passDefaultEnvs;
  }

  /** @deprecated Use {@link #setEnv(Map)} and {@link #setPassParentEnvs(boolean)} instead with already preprocessed variables */
  @Deprecated(forRemoval = true)
  public void setupEnvs(Map<String, String> envs, boolean passDefault) {
    if (!envs.isEmpty()) {
      envs = new HashMap<>(envs);
      EnvironmentUtil.inlineParentOccurrences(envs);
    }
    setEnv(envs);
    setPassParentEnvs(passDefault);
  }
}