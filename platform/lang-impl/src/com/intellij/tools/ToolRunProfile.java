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
package com.intellij.tools;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2005
 */
public class ToolRunProfile implements ModuleRunProfile{
  private final Tool myTool;
  private final GeneralCommandLine myCommandLine;

  public ToolRunProfile(final Tool tool, final DataContext context) {
    myTool = tool;
    myCommandLine = myTool.createCommandLine(context);
    if (context instanceof DataManagerImpl.MyDataContext) {
      // hack: macro.expand() can cause UI events such as showing dialogs ('Prompt' macro) which may 'invalidate' the datacontext
      // since we know exactly that context is valid, we need to update its event count
      ((DataManagerImpl.MyDataContext)context).setEventCount(IdeEventQueue.getInstance().getEventCount());
    }
  }

  public String getName() {
    return myTool.getName();
  }

  public Icon getIcon() {
    return null;
  }

  public void checkConfiguration() throws RuntimeConfigurationException {
  }

  @NotNull
  public Module[] getModules() {
    return Module.EMPTY_ARRAY;
  }

  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) {
    final Project project = env.getProject();
    if (project == null || myCommandLine == null) {
      // can return null if creation of cmd line has been cancelled
      return null;
    }

    final CommandLineState commandLineState = new CommandLineState(env) {
      GeneralCommandLine createCommandLine() {
        return myCommandLine;
      }

      protected OSProcessHandler startProcess() throws ExecutionException {
        final GeneralCommandLine commandLine = createCommandLine();
        final OSProcessHandler processHandler = new OSProcessHandler(commandLine.createProcess(), commandLine.getCommandLineString());
        ProcessTerminatedListener.attach(processHandler);
        return processHandler;
      }

      public ExecutionResult execute(@NotNull final Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        final ExecutionResult result = super.execute(executor, runner);
        final ProcessHandler processHandler = result.getProcessHandler();
        if (processHandler != null) {
          processHandler.addProcessListener(new ToolProcessAdapter(project, myTool.synchronizeAfterExecution(), getName()));
        }
        return result;
      }
    };
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    final FilterInfo[] outputFilters = myTool.getOutputFilters();
    for (int i = 0; i < outputFilters.length; i++) {
      builder.addFilter(new RegexpFilter(project, outputFilters[i].getRegExp()));
    }

    commandLineState.setConsoleBuilder(builder);
    return commandLineState;
  }

}
