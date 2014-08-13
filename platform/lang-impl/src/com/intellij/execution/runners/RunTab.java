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
package com.intellij.execution.runners;

import com.intellij.diagnostic.logging.LogConsoleManagerBase;
import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.diagnostic.logging.OutputFileUtil;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.SearchScopeProvider;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RunTab extends LogConsoleManagerBase implements DataProvider {
  @NotNull
  protected final RunnerLayoutUi myUi;
  protected final LogFilesManager myManager;
  protected RunContentDescriptor myRunContentDescriptor;

  protected RunTab(@NotNull ExecutionEnvironment environment, @NotNull String runnerType) {
    this(environment.getProject(),
         SearchScopeProvider.createSearchScope(environment.getProject(), environment.getRunProfile()),
         runnerType,
         environment.getExecutor().getId(),
         environment.getRunProfile().getName());

    setEnvironment(environment);
  }

  protected RunTab(@NotNull Project project, @NotNull GlobalSearchScope searchScope, @NotNull String runnerType, @NotNull String runnerTitle, @NotNull String sessionName) {
    super(project, searchScope);

    myManager = new LogFilesManager(project, this, this);

    myUi = RunnerLayoutUi.Factory.getInstance(getProject()).create(runnerType, runnerTitle, sessionName, this);
    myUi.getContentManager().addDataProvider(this);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (LangDataKeys.RUN_PROFILE.is(dataId)) {
      ExecutionEnvironment environment = getEnvironment();
      return environment == null ? null : environment.getRunProfile();
    }
    else if (LangDataKeys.EXECUTION_ENVIRONMENT.is(dataId)) {
      return getEnvironment();
    }
    else if (LangDataKeys.RUN_CONTENT_DESCRIPTOR.is(dataId)) {
      return myRunContentDescriptor;
    }
    return null;
  }

  @Override
  public final void setEnvironment(@NotNull ExecutionEnvironment environment) {
    super.setEnvironment(environment);

    RunProfile profile = environment.getRunProfile();
    if (profile instanceof RunConfigurationBase) {
      myManager.registerFileMatcher((RunConfigurationBase)profile);
    }
  }

  protected final void initLogConsoles(@NotNull RunProfile runConfiguration, @Nullable ProcessHandler processHandler, @Nullable ExecutionConsole console) {
    if (runConfiguration instanceof RunConfigurationBase && processHandler != null) {
      RunConfigurationBase configuration = (RunConfigurationBase)runConfiguration;
      myManager.initLogConsoles(configuration, processHandler);
      OutputFileUtil.attachDumpListener(configuration, processHandler, console);
    }
  }
}
