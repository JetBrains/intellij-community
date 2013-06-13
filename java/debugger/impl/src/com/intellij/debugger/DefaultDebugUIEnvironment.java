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
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.runners.RestartAction;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Constraints;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
public class DefaultDebugUIEnvironment implements DebugUIEnvironment {

  private final Project myProject;
  private final Executor myExecutor;
  private final ProgramRunner myRunner;
  private final ExecutionEnvironment myExecutionEnvironment;
  @Nullable private RunContentDescriptor myReuseContent;
  private final RunProfile myRunProfile;
  private final DebugEnvironment myModelEnvironment;

  public DefaultDebugUIEnvironment(Project project,
                                   Executor executor,
                                   ProgramRunner runner,
                                   ExecutionEnvironment environment,
                                   RunProfileState state,
                                   @Nullable RunContentDescriptor reuseContent,
                                   RemoteConnection remoteConnection,
                                   boolean pollConnection) {
    myProject = project;
    myExecutor = executor;
    myRunner = runner;
    myExecutionEnvironment = environment;
    myRunProfile = environment.getRunProfile();
    myModelEnvironment = new DefaultDebugEnvironment(project,
                                                     executor,
                                                     runner,
                                                     myRunProfile,
                                                     state,
                                                     remoteConnection,
                                                     pollConnection);
    myReuseContent = reuseContent;
    if (myReuseContent != null) {
      Disposer.register(myReuseContent, new Disposable() {
        @Override
        public void dispose() {
          myReuseContent = null;
        }
      });
    }
  }

  @Override
  public DebugEnvironment getEnvironment() {
    return myModelEnvironment;
  }

  @Nullable
  @Override
  public RunContentDescriptor getReuseContent() {
    return myReuseContent;
  }

  @Override
  public Icon getIcon() {
    return myRunProfile.getIcon();
  }

  @Override
  public void initLogs(RunContentDescriptor content, LogFilesManager logFilesManager) {
    ProcessHandler processHandler = content.getProcessHandler();
    if (myRunProfile instanceof RunConfigurationBase) {
      RunConfigurationBase runConfiguration = (RunConfigurationBase)myRunProfile;

      logFilesManager.registerFileMatcher(runConfiguration);

      logFilesManager.initLogConsoles(runConfiguration, processHandler);
      OutputFileUtil.attachDumpListener(runConfiguration, processHandler, content.getExecutionConsole());
    }
  }

  @Override
  public void initActions(RunContentDescriptor content, DefaultActionGroup actionGroup) {
    RestartAction restartAction = new RestartAction(myExecutor,
                                                    myRunner,
                                                    content,
                                                    myExecutionEnvironment);
    actionGroup.add(restartAction, Constraints.FIRST);
    restartAction.registerShortcut(content.getComponent());

    actionGroup.add(new CloseAction(myExecutor, content, myProject));
    actionGroup.add(new ContextHelpAction(myExecutor.getHelpId()));
  }

  @Override
  public RunProfile getRunProfile() {
    return myRunProfile;
  }
}
