/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ShortenCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;

/**
 * @author Gregory.Shrago
 */
public class SimpleJavaParameters extends SimpleProgramParameters {

  private Sdk myJdk;
  private String myMainClass;
  private final PathsList myClassPath = new PathsList();
  private String myModuleName;
  private final PathsList myModulePath = new PathsList();
  private final ParametersList myVmParameters = new ParametersList();
  private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();
  private boolean myUseDynamicClasspath;
  private boolean myUseDynamicVMOptions;
  private boolean myUseDynamicParameters;
  private boolean myUseClasspathJar;
  private boolean myArgFile;
  private boolean myClasspathFile = true;
  private String myJarPath;

  @Nullable
  public Sdk getJdk() {
    return myJdk;
  }

  public void setJdk(Sdk jdk) {
    myJdk = jdk;
  }

  public String getMainClass() {
    return myMainClass;
  }

  public void setMainClass(String mainClass) {
    myMainClass = mainClass;
  }

  public PathsList getClassPath() {
    return myClassPath;
  }

  public String getModuleName() {
    return myModuleName;
  }

  public void setModuleName(String moduleName) {
    myModuleName = moduleName;
  }

  public PathsList getModulePath() {
    return myModulePath;
  }

  public ParametersList getVMParametersList() {
    return myVmParameters;
  }

  @Nullable
  public Charset getCharset() {
    return myCharset;
  }

  public void setCharset(@Nullable Charset charset) {
    myCharset = charset;
  }

  public boolean isDynamicClasspath() {
    return myUseDynamicClasspath;
  }

  public void setUseDynamicClasspath(boolean useDynamicClasspath) {
    myUseDynamicClasspath = useDynamicClasspath && (myArgFile || myUseClasspathJar || myClasspathFile);
  }

  public void setUseDynamicClasspath(@Nullable Project project) {
    setUseDynamicClasspath(JdkUtil.useDynamicClasspath(project));
  }

  public boolean isDynamicVMOptions() {
    return myUseDynamicVMOptions;
  }

  /** Allows to pass system properties via a temporary file in order to avoid "too long command line" problem. */
  public void setUseDynamicVMOptions(boolean useDynamicVMOptions) {
    myUseDynamicVMOptions = useDynamicVMOptions;
  }

  public boolean isDynamicParameters() {
    return myUseDynamicParameters;
  }

  /** Allows to pass program parameters via a temporary file in order to avoid "too long command line" problem. */
  public void setUseDynamicParameters(boolean useDynamicParameters) {
    myUseDynamicParameters = useDynamicParameters;
  }

  public boolean isUseClasspathJar() {
    return myUseClasspathJar;
  }

  public boolean isArgFile() {
    return myArgFile;
  }

  /**
   * Option to use java 9 @argFile
   */
  public void setArgFile(boolean argFile) {
    myArgFile = argFile;
  }

  public boolean isClasspathFile() {
    return myClasspathFile;
  }

  public void setClasspathFile(boolean classpathFile) {
    myClasspathFile = classpathFile;
  }

  /**
   * Allows to use a specially crafted .jar file instead of a custom class loader to pass classpath/properties/parameters.
   * Would have no effect if user explicitly disabled idea.dynamic.classpath.jar
   */
  public void setUseClasspathJar(boolean useClasspathJar) {
    myUseClasspathJar = useClasspathJar && JdkUtil.useClasspathJar();
  }

  public void setShortenCommandLine(@Nullable ShortenCommandLine mode, Project project) {
    if (mode == null) {
      Sdk jdk = getJdk();
      mode = ShortenCommandLine.getDefaultMethod(project, jdk != null ? jdk.getHomePath() : null);
    }
    myUseDynamicClasspath = mode != ShortenCommandLine.NONE;
    myUseClasspathJar = mode == ShortenCommandLine.MANIFEST;
    setClasspathFile(mode == ShortenCommandLine.CLASSPATH_FILE);
    setArgFile(mode == ShortenCommandLine.ARGS_FILE);
  }

  public String getJarPath() {
    return myJarPath;
  }

  public void setJarPath(String jarPath) {
    myJarPath = jarPath;
  }

  /**
   * @throws CantRunException when incorrect Java SDK is specified
   * @see JdkUtil#setupJVMCommandLine(SimpleJavaParameters)
   */
  @NotNull
  public GeneralCommandLine toCommandLine() throws CantRunException {
    return JdkUtil.setupJVMCommandLine(this);
  }

  @NotNull
  public OSProcessHandler createOSProcessHandler() throws ExecutionException {
    OSProcessHandler processHandler = new OSProcessHandler(toCommandLine());
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }

}