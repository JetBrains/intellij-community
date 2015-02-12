/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class RunTab implements DataProvider, Disposable {
  @NotNull
  protected final RunnerLayoutUi myUi;
  private LogFilesManager myManager;
  protected RunContentDescriptor myRunContentDescriptor;

  protected ExecutionEnvironment myEnvironment;
  protected final Project myProject;
  private final GlobalSearchScope mySearchScope;

  private LogConsoleManagerBase logConsoleManager;

  protected RunTab(@NotNull ExecutionEnvironment environment, @NotNull String runnerType) {
    this(environment.getProject(),
         SearchScopeProvider.createSearchScope(environment.getProject(), environment.getRunProfile()),
         runnerType,
         environment.getExecutor().getId(),
         environment.getRunProfile().getName());

    myEnvironment = environment;
  }

  @Override
  public void dispose() {
    myRunContentDescriptor = null;
    myEnvironment = null;
    logConsoleManager = null;
  }

  protected RunTab(@NotNull Project project, @NotNull GlobalSearchScope searchScope, @NotNull String runnerType, @NotNull String runnerTitle, @NotNull String sessionName) {
    myProject = project;
    mySearchScope = searchScope;

    myUi = RunnerLayoutUi.Factory.getInstance(project).create(runnerType, runnerTitle, sessionName, this);
    myUi.getContentManager().addDataProvider(this);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (LangDataKeys.RUN_PROFILE.is(dataId)) {
      return myEnvironment == null ? null : myEnvironment.getRunProfile();
    }
    else if (LangDataKeys.EXECUTION_ENVIRONMENT.is(dataId)) {
      return myEnvironment;
    }
    else if (LangDataKeys.RUN_CONTENT_DESCRIPTOR.is(dataId)) {
      return myRunContentDescriptor;
    }
    return null;
  }

  @NotNull
  public LogConsoleManagerBase getLogConsoleManager() {
    if (logConsoleManager == null) {
      logConsoleManager = new LogConsoleManagerBase(myProject, mySearchScope) {
        @Override
        protected Icon getDefaultIcon() {
          return AllIcons.Debugger.Console_log;
        }

        @Override
        protected RunnerLayoutUi getUi() {
          return myUi;
        }

        @Override
        public ProcessHandler getProcessHandler() {
          return myRunContentDescriptor == null ? null : myRunContentDescriptor.getProcessHandler();
        }
      };
    }
    return logConsoleManager;
  }

  protected final void initLogConsoles(@NotNull RunProfile runConfiguration, @NotNull RunContentDescriptor contentDescriptor, @Nullable ExecutionConsole console) {
    ProcessHandler processHandler = contentDescriptor.getProcessHandler();
    if (runConfiguration instanceof RunConfigurationBase) {
      RunConfigurationBase configuration = (RunConfigurationBase)runConfiguration;
      if (myManager == null) {
        myManager = new LogFilesManager(myProject, getLogConsoleManager(), contentDescriptor);
      }
      myManager.addLogConsoles(configuration, processHandler);
      if (processHandler != null) {
        OutputFileUtil.attachDumpListener(configuration, processHandler, console);
      }
    }
  }
}
