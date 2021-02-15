// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.configurations;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ShortenCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.execution.target.TargetEnvironmentRequest;
import com.intellij.execution.target.TargetedCommandLineBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JdkUtil;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.ApiStatus;
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

  private final JavaTargetDependentParameters myTargetDependentParameters = new JavaTargetDependentParameters();

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

  /**
   * Enables command line shortening considering current {@link #getJdk JDK}
   * and ignoring whether dynamic classpath is {@linkplain JdkUtil#useDynamicClasspath enabled on project or application level}.
   */
  public void useDynamicClasspathDefinedByJdkLevel() {
    Sdk jdk = getJdk();
    setShortenCommandLine(ShortenCommandLine.getDefaultMethodForJdkLevel(jdk != null ? jdk.getHomePath() : null));
  }

  public void setUseDynamicClasspath(boolean useDynamicClasspath) {
    myUseDynamicClasspath = useDynamicClasspath && (myArgFile || myUseClasspathJar || myClasspathFile);
  }

  public void setUseDynamicClasspath(@Nullable Project project) {
    Sdk jdk = getJdk();
    ShortenCommandLine mode = myArgFile || myUseClasspathJar || myClasspathFile
                              ? ShortenCommandLine.getDefaultMethod(project, jdk != null ? jdk.getHomePath() : null)
                              : ShortenCommandLine.NONE;  // explicitly disabled in the UI
    setShortenCommandLine(mode, project);
  }

  public boolean isDynamicVMOptions() {
    return myUseDynamicVMOptions;
  }

  /** Allows passing system properties via a temporary file in order to avoid "too long command line" problem. */
  public void setUseDynamicVMOptions(boolean useDynamicVMOptions) {
    myUseDynamicVMOptions = useDynamicVMOptions;
  }

  public boolean isDynamicParameters() {
    return myUseDynamicParameters;
  }

  /** Allows passing program parameters via a temporary file in order to avoid "too long command line" problem. */
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

  public void setShortenCommandLine(@Nullable ShortenCommandLine mode, @Nullable Project project) {
    if (mode == null) {
      Sdk jdk = getJdk();
      mode = ShortenCommandLine.getDefaultMethod(project, jdk != null ? jdk.getHomePath() : null);
    }
    setShortenCommandLine(mode);
  }

  public void setShortenCommandLine(@NotNull ShortenCommandLine mode) {
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

  @ApiStatus.Experimental
  public JavaTargetDependentParameters getTargetDependentParameters() {
    return myTargetDependentParameters;
  }

  /**
   * Consider using {@link #toCommandLine(TargetEnvironmentRequest, TargetEnvironmentConfiguration)} instead with request created by {@link com.intellij.execution.target.local.LocalTargetEnvironmentFactory} as an argument
   *
   * @throws CantRunException when incorrect Java SDK is specified
   * @see JdkUtil#setupJVMCommandLine(SimpleJavaParameters)
   */
  @NotNull
  public GeneralCommandLine toCommandLine() throws CantRunException {
    return JdkUtil.setupJVMCommandLine(this);
  }

  /**
   * @throws CantRunException when incorrect Java SDK is specified
   * @see JdkUtil#setupJVMCommandLine(SimpleJavaParameters)
   */
  @NotNull
  public TargetedCommandLineBuilder toCommandLine(@NotNull TargetEnvironmentRequest request, @Nullable TargetEnvironmentConfiguration configuration)
    throws CantRunException {
    return JdkUtil.setupJVMCommandLine(this, request, configuration);
  }

  @NotNull
  public OSProcessHandler createOSProcessHandler() throws ExecutionException {
    OSProcessHandler processHandler = new OSProcessHandler(toCommandLine());
    ProcessTerminatedListener.attach(processHandler);
    return processHandler;
  }
}