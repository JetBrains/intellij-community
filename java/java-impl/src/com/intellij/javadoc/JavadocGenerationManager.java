/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.javadoc;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(name = "JavadocGenerationManager")
public final class JavadocGenerationManager implements PersistentStateComponent<JavadocConfiguration> {
  private JavadocConfiguration myConfiguration = new JavadocConfiguration();
  private final Project myProject;

  public static JavadocGenerationManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, JavadocGenerationManager.class);
  }

  JavadocGenerationManager(Project project) {
    myProject = project;
  }

  @Override
  public JavadocConfiguration getState() {
    return myConfiguration;
  }

  @Override
  public void loadState(@NotNull JavadocConfiguration state) {
    myConfiguration = state;
  }

  @NotNull
  public JavadocConfiguration getConfiguration() {
    return myConfiguration;
  }

  public void generateJavadoc(AnalysisScope scope) {
    try {
      JavadocGeneratorRunProfile profile = new JavadocGeneratorRunProfile(myProject, scope, myConfiguration);
      ExecutionEnvironmentBuilder.create(myProject, DefaultRunExecutor.getRunExecutorInstance(), profile).buildAndExecute();
    }
    catch (ExecutionException e) {
      ExecutionErrorDialog.show(e, CommonBundle.getErrorTitle(), myProject);
    }
  }
}