/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Gregory.Shrago
 */
public class SimpleJavaParameters extends SimpleProgramParameters {
  private Sdk myJdk;
  private final PathsList myClassPath = new PathsList();
  private String myMainClass;
  private final ParametersList myVmParameters = new ParametersList();
  private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();
  private Map<String, String> myEnv;
  private boolean myPassParentEnvs = true;

  public String getMainClass() {
    return myMainClass;
  }

  /**
   * @return jdk used to launch the application.
   * If the instance of the JavaParameters is used to configure app server startup script,
   * then null is returned.
   */
  @Nullable
  public Sdk getJdk() {
    return myJdk;
  }

  public void setJdk(final Sdk jdk) {
    myJdk = jdk;
  }

  public void setMainClass(@NonNls final String mainClass) {
    myMainClass = mainClass;
  }

  public ParametersList getVMParametersList() {
    return myVmParameters;
  }

  public Charset getCharset() {
    return myCharset;
  }

  public void setCharset(final Charset charset) {
    myCharset = charset;
  }

  public PathsList getClassPath() {
    return myClassPath;
  }

  public Map<String, String> getEnv() {
    return myEnv;
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

  public void setupEnvs(Map<String, String> envs, boolean passDefault) {
    if (!envs.isEmpty()) {
      final HashMap<String, String> map = new HashMap<String, String>(envs);
      EnvironmentVariablesComponent.inlineParentOccurrences(map);
      setEnv(map);
      setPassParentEnvs(passDefault);
    }
  }
}
