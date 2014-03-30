/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.execution.Executor;
import com.intellij.execution.RunnerRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.util.ExecutionErrorDialog;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

@State(name = "JavadocGenerationManager",
       storages = {
         @Storage(
           file = StoragePathMacros.PROJECT_FILE
         )
       }
)
public final class JavadocGenerationManager implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.javadoc.JavadocGenerationManager");
  private final JavadocConfiguration myConfiguration;
  private final Project myProject;

  public static JavadocGenerationManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, JavadocGenerationManager.class);
  }

  JavadocGenerationManager(Project project) {
    myProject = project;
    myConfiguration = new JavadocConfiguration(project);
  }

  @Override
  public Element getState() {
    final Element state = new Element("state");
    try {
      myConfiguration.writeExternal(state);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
    return state;
  }

  @Override
  public void loadState(Element state) {
    try {
      myConfiguration.readExternal(state);
    }
    catch (InvalidDataException e) {
      LOG.error(e);
    }
  }

  public JavadocConfiguration getConfiguration() {
    return myConfiguration;
  }

  public void generateJavadoc(AnalysisScope scope) {
    myConfiguration.setGenerationScope(scope);
    try {
      final ProgramRunner runner = RunnerRegistry.getInstance().getRunner(DefaultRunExecutor.EXECUTOR_ID, myConfiguration);
      assert runner != null;
      Executor executor = DefaultRunExecutor.getRunExecutorInstance();
      runner.execute(new ExecutionEnvironment(myConfiguration ,executor, myProject, null));
    }
    catch (ExecutionException e) {
      ExecutionErrorDialog.show(e, CommonBundle.getErrorTitle(), myProject);
    }
  }
}
