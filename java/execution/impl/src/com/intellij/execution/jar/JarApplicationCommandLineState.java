/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.execution.jar;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.application.BaseJavaApplicationCommandLineState;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.util.JavaParametersUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class JarApplicationCommandLineState extends BaseJavaApplicationCommandLineState<JarApplicationConfiguration> {
  public JarApplicationCommandLineState(@NotNull final JarApplicationConfiguration configuration, final ExecutionEnvironment environment) {
    super(environment, configuration);
  }

  @Override
  protected JavaParameters createJavaParameters() throws ExecutionException {
    final JavaParameters params = new JavaParameters();
    final String jreHome = myConfiguration.isAlternativeJrePathEnabled() ? myConfiguration.getAlternativeJrePath() : null;
    params.setJdk(JavaParametersUtil.createProjectJdk(myConfiguration.getProject(), jreHome));
    setupJavaParameters(params);
    params.setJarPath(FileUtil.toSystemDependentName(myConfiguration.getJarPath()));
    return params;
  }
}
