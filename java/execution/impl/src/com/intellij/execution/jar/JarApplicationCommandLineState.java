// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jar;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.application.BaseJavaApplicationCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

public class JarApplicationCommandLineState extends BaseJavaApplicationCommandLineState<JarApplicationConfiguration> {
  public JarApplicationCommandLineState(final @NotNull JarApplicationConfiguration configuration, final ExecutionEnvironment environment) {
    super(environment, configuration);
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters params = new JavaParameters();
    final String jreHome = myConfiguration.isAlternativeJrePathEnabled() ? myConfiguration.getAlternativeJrePath() : null;
    params.setJdk(JavaParametersUtil.createProjectJdk(myConfiguration.getProject(), jreHome));
    JavaParametersUtil.configureConfiguration(params, myConfiguration);
    setupJavaParameters(params);
    params.setJarPath(FileUtil.toSystemDependentName(myConfiguration.getJarPath()));
    return params;
  }
}
