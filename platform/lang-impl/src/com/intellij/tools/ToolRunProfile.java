/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacroManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2005
 */
public class ToolRunProfile implements ModuleRunProfile{
  private static final Logger LOG = Logger.getInstance("#com.intellij.tools.ToolRunProfile");
  private final Tool myTool;
  private final DataContext myContext;
  private final GeneralCommandLine myCommandLine;

  public ToolRunProfile(final Tool tool, final DataContext context) {
    myTool = tool;
    myContext = context;
    myCommandLine = myTool.createCommandLine(context);
    //if (context instanceof DataManagerImpl.MyDataContext) {
    //  // hack: macro.expand() can cause UI events such as showing dialogs ('Prompt' macro) which may 'invalidate' the datacontext
    //  // since we know exactly that context is valid, we need to update its event count
    //  ((DataManagerImpl.MyDataContext)context).setEventCount(IdeEventQueue.getInstance().getEventCount());
    //}
  }

  @Override
  public String getName() {
    return expandMacrosInName(myTool, myContext);
  }

  public static String expandMacrosInName(Tool tool, DataContext context) {
    String name = tool.getName();
    try {
      return MacroManager.getInstance().expandMacrosInString(name, true, context);
    }
    catch (Macro.ExecutionCancelledException e) {
      LOG.info(e);
      return name;
    }
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public RunProfileState getState(@NotNull final Executor executor, @NotNull final ExecutionEnvironment env) {
    final Project project = env.getProject();
    if (myCommandLine == null) {
      // can return null if creation of cmd line has been cancelled
      return null;
    }

    final CommandLineState commandLineState = new CommandLineState(env) {
      GeneralCommandLine createCommandLine() {
        return myCommandLine;
      }

      @Override
      @NotNull
      protected OSProcessHandler startProcess() throws ExecutionException {
        final GeneralCommandLine commandLine = createCommandLine();
        final OSProcessHandler processHandler = new ColoredProcessHandler(commandLine);
        ProcessTerminatedListener.attach(processHandler);
        return processHandler;
      }

      @Override
      @NotNull
      public ExecutionResult execute(@NotNull final Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
        final ExecutionResult result = super.execute(executor, runner);
        final ProcessHandler processHandler = result.getProcessHandler();
        if (processHandler != null) {
          processHandler.addProcessListener(new ToolProcessAdapter(project, myTool.synchronizeAfterExecution(), getName()));
          processHandler.addProcessListener(new ProcessAdapter() {
            @Override
            public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
              if ((outputType == ProcessOutputTypes.STDOUT && myTool.isShowConsoleOnStdOut())
                || (outputType == ProcessOutputTypes.STDERR && myTool.isShowConsoleOnStdErr())) {
                ExecutionManager.getInstance(project).getContentManager().toFrontRunContent(executor, processHandler);
              }
            }
          });
        }
        return result;
      }
    };
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    final FilterInfo[] outputFilters = myTool.getOutputFilters();
    for (FilterInfo outputFilter : outputFilters) {
      builder.addFilter(new RegexpFilter(project, outputFilter.getRegExp()));
    }

    commandLineState.setConsoleBuilder(builder);
    return commandLineState;
  }

}
