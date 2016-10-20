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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.projectRoots.JavaSdkType;
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
  private static final Logger LOG = Logger.getInstance(SimpleJavaParameters.class);

  private Sdk myJdk;
  private String myMainClass;
  private final PathsList myClassPath = new PathsList();
  private String myModuleName;
  private final PathsList myModulePath = new PathsList();
  private final ParametersList myVmParameters = new ParametersList();
  private Charset myCharset = CharsetToolkit.getDefaultSystemCharset();
  private boolean myUseDynamicClasspath;
  private boolean myUseDynamicVMOptions;
  private boolean myUseClasspathJar = false;
  private boolean myPassProgramParametersViaClasspathJar;
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

  public void setUseDynamicClasspath(boolean useDynamicClasspath) {
    myUseDynamicClasspath = useDynamicClasspath;
  }

  public boolean isDynamicVMOptions() {
    return myUseDynamicVMOptions;
  }

  public void setUseDynamicVMOptions(boolean useDynamicVMOptions) {
    myUseDynamicVMOptions = useDynamicVMOptions;
  }

  public boolean isUseClasspathJar() {
    return myUseClasspathJar;
  }

  /**
   * Call this method and pass {@code true} to pass classpath of the application via MANIFEST.MF file in a specially generated classpath.jar
   * archive instead of passing it via -classpath command line option. This may be needed to avoid problems with too long command line on Windows.
   */
  public void setUseClasspathJar(boolean useClasspathJar) {
    myUseClasspathJar = useClasspathJar;
  }

  public boolean isPassProgramParametersViaClasspathJar() {
    return myPassProgramParametersViaClasspathJar;
  }

  /**
   * Call this method and pass {@code true} to pass program parameters via attribute in MANIFEST.MF of the classpath jar instead of passing
   * them via command line. This may be needed to avoid problems with too long command line on Windows.
   */
  public void setPassProgramParametersViaClasspathJar(@SuppressWarnings("SameParameterValue") boolean passProgramParametersViaClasspathJar) {
    LOG.assertTrue(myUseClasspathJar);
    myPassProgramParametersViaClasspathJar = passProgramParametersViaClasspathJar;
  }

  public String getJarPath() {
    return myJarPath;
  }

  public void setJarPath(String jarPath) {
    myJarPath = jarPath;
  }

  @NotNull
  public GeneralCommandLine toCommandLine() {
    Sdk jdk = getJdk();
    if (jdk == null) throw new IllegalArgumentException("SDK should be defined");
    String exePath = ((JavaSdkType)jdk.getSdkType()).getVMExecutablePath(jdk);
    return JdkUtil.setupJVMCommandLine(exePath, this, myUseDynamicClasspath);
  }

  @NotNull
  public OSProcessHandler createOSProcessHandler() throws ExecutionException {
    OSProcessHandler processHandler = new OSProcessHandler(toCommandLine());
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }
}