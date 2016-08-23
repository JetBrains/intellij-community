/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.execution.configurations;

import com.intellij.util.EnvironmentUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SimpleProgramParameters {
  private final ParametersList myProgramParameters = new ParametersList();
  private String myWorkingDirectory;
  private Map<String, String> myEnv = new THashMap<>();
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

  /**
  * @deprecated Use {@link #setEnv(Map)} and {@link #setPassParentEnvs(boolean)} instead with already preprocessed variables.
  */
  @Deprecated 
  public void setupEnvs(Map<String, String> envs, boolean passDefault) {
    if (!envs.isEmpty()) {
      envs = new HashMap<>(envs);
      EnvironmentUtil.inlineParentOccurrences(envs);
    }
    setEnv(envs);
    setPassParentEnvs(passDefault);
  }
}
