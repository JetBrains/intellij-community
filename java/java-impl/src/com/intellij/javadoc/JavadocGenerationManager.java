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
package com.intellij.javadoc;

import com.intellij.CommonBundle;
import com.intellij.analysis.AnalysisScope;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public final class JavadocGenerationManager extends AbstractProjectComponent implements JDOMExternalizable {
  private final JavadocConfiguration myConfiguration;

  public static JavadocGenerationManager getInstance(Project project) {
    return project.getComponent(JavadocGenerationManager.class);
  }

  JavadocGenerationManager(Project project) {
    super(project);
    myConfiguration = new JavadocConfiguration(project);
  }

  @NotNull
  public String getComponentName() {
    return "JavadocGenerationManager";
  }

  public void readExternal(Element element) throws InvalidDataException {
    myConfiguration.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myConfiguration.writeExternal(element);
  }

  public JavadocConfiguration getConfiguration() {
    return myConfiguration;
  }

  public void generateJavadoc(AnalysisScope scope) {
    myConfiguration.setGenerationScope(scope);
    try {
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(DefaultRunExecutor.EXECUTOR_ID, myConfiguration);
      assert runner != null;
      runner.execute(DefaultRunExecutor.getRunExecutorInstance(), new ExecutionEnvironment(myConfiguration, myProject, null, null, null));
    }
    catch (ExecutionException e) {
      ExecutionErrorDialog.show(e, CommonBundle.getErrorTitle(), myProject);
    }
  }
}
