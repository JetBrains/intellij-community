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
package com.intellij.debugger;

import com.intellij.diagnostic.logging.LogFilesManager;
import com.intellij.diagnostic.logging.OutputFileUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.ExceptionFilters;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RestartAction;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public class DefaultDebugEnvironment implements DebugEnvironment {

  private final GlobalSearchScope mySearchScope;
  private final Project myProject;
  private final Executor myExecutor;
  private final ProgramRunner myRunner;
  @Nullable private final ExecutionEnvironment myEnvironment;
  private RunProfileState myState;
  @Nullable private final RunContentDescriptor myReuseContent;
  private final RemoteConnection myRemoteConnection;
  private final boolean myPollConnection;
  private final RunProfile myRunProfile;

  public DefaultDebugEnvironment(Project project,
                                 Executor executor,
                                 ProgramRunner runner,
                                 ExecutionEnvironment environment,
                                 RunProfileState state,
                                 @Nullable RunContentDescriptor reuseContent,
                                 RemoteConnection remoteConnection,
                                 boolean pollConnection) {
    this(project,
         executor,
         runner,
         environment,
         environment.getRunProfile(),
         state,
         reuseContent,
         remoteConnection,
         pollConnection);
  }

  public DefaultDebugEnvironment(Project project,
                                 Executor executor,
                                 ProgramRunner runner,
                                 RunProfile runProfile,
                                 RunProfileState state,
                                 @Nullable RunContentDescriptor reuseContent,
                                 RemoteConnection remoteConnection,
                                 boolean pollConnection) {
    this(project,
         executor,
         runner,
         null,
         runProfile,
         state,
         reuseContent,
         remoteConnection,
         pollConnection);
  }

  private DefaultDebugEnvironment(Project project,
                                  Executor executor,
                                  ProgramRunner runner,
                                  @Nullable ExecutionEnvironment environment,
                                  RunProfile runProfile,
                                  RunProfileState state,
                                  @Nullable RunContentDescriptor reuseContent,
                                  RemoteConnection remoteConnection,
                                  boolean pollConnection) {
    myProject = project;
    myExecutor = executor;
    myRunner = runner;
    myEnvironment = environment;
    myRunProfile = runProfile;
    myState = state;
    myReuseContent = reuseContent;
    myRemoteConnection = remoteConnection;
    myPollConnection = pollConnection;

    Module[] modules = null;
    if (myRunProfile instanceof ModuleRunProfile) {
      modules = ((ModuleRunProfile)myRunProfile).getModules();
    }
    if (modules == null || modules.length == 0) {
      mySearchScope = GlobalSearchScope.allScope(project);
    }
    else {
      GlobalSearchScope scope = GlobalSearchScope.moduleRuntimeScope(modules[0], true);
      for (int idx = 1; idx < modules.length; idx++) {
        Module module = modules[idx];
        scope = scope.uniteWith(GlobalSearchScope.moduleRuntimeScope(module, true));
      }
      mySearchScope = scope;
    }
  }

  @Override
  public ExecutionResult createExecutionResult() throws ExecutionException {
    if (myState instanceof CommandLineState) {
      final TextConsoleBuilder consoleBuilder = ((CommandLineState)myState).getConsoleBuilder();
      if (consoleBuilder != null) {
        List<Filter> filters = ExceptionFilters.getFilters(mySearchScope);
        for (Filter filter : filters) {
          consoleBuilder.addFilter(filter);
        }
      }
    }
    return myState.execute(myExecutor, myRunner);
  }

  @Override
  public GlobalSearchScope getSearchScope() {
    return mySearchScope;
  }

  @Override
  public boolean isRemote() {
    return myState instanceof RemoteState;
  }

  @Nullable
  @Override
  public RunContentDescriptor getReuseContent() {
    return myReuseContent;
  }

  @Override
  public RemoteConnection getRemoteConnection() {
    return myRemoteConnection;
  }

  @Override
  public boolean isPollConnection() {
    return myPollConnection;
  }

  @Override
  public String getSessionName() {
    return myRunProfile.getName();
  }

  @Override
  public Icon getIcon() {
    return myRunProfile.getIcon();
  }

  @Override
  public void initContent(RunContentDescriptor content, LogFilesManager logFilesManager, DefaultActionGroup actionGroup) {
    ProcessHandler processHandler = content.getProcessHandler();
    if (myRunProfile instanceof RunConfigurationBase) {
      RunConfigurationBase runConfiguration = (RunConfigurationBase)myRunProfile;

      logFilesManager.registerFileMatcher(runConfiguration);

      logFilesManager.initLogConsoles(runConfiguration, processHandler);
      OutputFileUtil.attachDumpListener(runConfiguration, processHandler, content.getExecutionConsole());
    }

    RestartAction restartAction = new RestartAction(myExecutor,
                                                    myRunner,
                                                    processHandler,
                                                    XDebuggerUIConstants.DEBUG_AGAIN_ICON,
                                                    content,
                                                    myEnvironment);
    actionGroup.add(restartAction, Constraints.FIRST);
    restartAction.registerShortcut(content.getComponent());

    actionGroup.add(new CloseAction(myExecutor, content, myProject));
    actionGroup.add(new ContextHelpAction(myExecutor.getHelpId()));
  }
}
