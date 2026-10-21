// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ModuleRunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.execution.filters.TextConsoleBuilder;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.ide.macro.Macro;
import com.intellij.ide.macro.MacroManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/**
 * @author Eugene Zhuravlev
 */
@ApiStatus.Internal
public class ToolRunProfile implements ModuleRunProfile{
  private static final Logger LOG = Logger.getInstance(ToolRunProfile.class);
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
  public @NotNull String getName() {
    return expandMacrosInName(myTool, myContext);
  }

  public static @NlsSafe String expandMacrosInName(Tool tool, DataContext context) {
    String name = tool.getName();
    if (name != null && name.contains("$")) {
      try {
        return MacroManager.getInstance().expandMacrosInString(name, true, context);
      }
      catch (Macro.ExecutionCancelledException e) {
        LOG.info(e);
      }
    }
    return name;
  }

  @Override
  public Icon getIcon() {
    return null;
  }

  @Override
  public RunProfileState getState(final @NotNull Executor executor, final @NotNull ExecutionEnvironment env) {
    final Project project = env.getProject();
    if (myCommandLine == null) {
      // can return null if creation of cmd line has been cancelled
      return null;
    }

    final CommandLineState commandLineState = new ToolCommandLineState(env, myTool, myCommandLine, getName());
    TextConsoleBuilder builder = TextConsoleBuilderFactory.getInstance().createBuilder(project);
    final FilterInfo[] outputFilters = myTool.getOutputFilters();
    for (FilterInfo outputFilter : outputFilters) {
      builder.addFilter(new RegexpFilter(project, outputFilter.getRegExp()));
    }

    commandLineState.setConsoleBuilder(builder);
    return commandLineState;
  }

}
