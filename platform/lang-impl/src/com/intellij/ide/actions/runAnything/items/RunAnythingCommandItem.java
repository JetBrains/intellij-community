// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.items;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.runners.ExecutionEnvironmentBuilder;
import com.intellij.execution.util.ExecUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.runAnything.RunAnythingAction;
import com.intellij.ide.actions.runAnything.RunAnythingCache;
import com.intellij.ide.actions.runAnything.RunAnythingUtil;
import com.intellij.ide.actions.runAnything.commands.RunAnythingCommandCustomizer;
import com.intellij.ide.actions.runAnything.execution.RunAnythingRunProfile;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class RunAnythingCommandItem extends RunAnythingItem<String> {
  private static final Logger LOG = Logger.getInstance(RunAnythingCommandItem.class);
  @Nullable private final Module myModule;
  @NotNull private final String myCommandLine;
  @NotNull private final Project myProject;
  public static final Icon UNDEFINED_COMMAND_ICON = AllIcons.Actions.Run_anything;

  public RunAnythingCommandItem(@NotNull Project project, @Nullable Module module, @NotNull String commandLine) {
    myProject = project;
    myModule = module;
    myCommandLine = commandLine;
  }

  @Override
  public void run(@NotNull DataContext dataContext) {
    super.run(dataContext);

    VirtualFile workDirectory = dataContext.getData(CommonDataKeys.VIRTUAL_FILE);
    Executor executor = dataContext.getData(RunAnythingAction.EXECUTOR_KEY);
    LOG.assertTrue(workDirectory != null);
    LOG.assertTrue(executor != null);

    runCommand(workDirectory, myCommandLine, executor, dataContext);
  }

  public static void runCommand(@NotNull VirtualFile workDirectory,
                                @NotNull String commandString,
                                @NotNull Executor executor,
                                @NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    LOG.assertTrue(project != null);

    Collection<String> commands = RunAnythingCache.getInstance(project).getState().getCommands();
    commands.remove(commandString);
    commands.add(commandString);

    dataContext = RunAnythingCommandCustomizer.customizeContext(dataContext);

    GeneralCommandLine initialCommandLine =
      new GeneralCommandLine(commandString).withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
    GeneralCommandLine commandLine = RunAnythingCommandCustomizer.customizeCommandLine(dataContext, workDirectory, initialCommandLine);
    try {
      ExecutionEnvironmentBuilder.create(project, executor, new RunAnythingRunProfile(commandLine, commandString))
                                 .dataContext(dataContext)
                                 .buildAndExecute();
    }
    catch (ExecutionException e) {
      LOG.warn(e);
      Messages.showInfoMessage(project, e.getMessage(), IdeBundle.message("run.anything.console.error.title"));
    }
  }

  @NotNull
  public static List<String> getShellCommand() {
    if (SystemInfoRt.isWindows) return ContainerUtil.immutableList(ExecUtil.getWindowsShellName(), "/c");

    String shell = System.getenv("SHELL");
    if (shell == null || !new File(shell).canExecute()) {
      return ContainerUtil.emptyList();
    }

    List<String> commands = ContainerUtil.newArrayList(shell);
    if (Registry.is("run.anything.bash.login.mode", false)) {
      if (!shell.endsWith("/tcsh") && !shell.endsWith("/csh")) {
        commands.add("--login");
      }
    }
    commands.add("-c");
    return commands;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    RunAnythingCommandItem item = (RunAnythingCommandItem)o;
    return Objects.equals(myModule, item.myModule) &&
           Objects.equals(myCommandLine, item.myCommandLine) &&
           Objects.equals(myProject, item.myProject);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myModule, myCommandLine, myProject);
  }

  @NotNull
  @Override
  public String getText() {
    return myCommandLine;
  }

  @NotNull
  @Override
  public String getAdText() {
    return RunAnythingAction.AD_MODULE_CONTEXT + " , " + RunAnythingAction.AD_DEBUG_TEXT + ", " + RunAnythingAction.AD_REMOVE_COMMAND;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return UNDEFINED_COMMAND_ICON;
  }

  @NotNull
  @Override
  public String getValue() {
    return myCommandLine;
  }

  @Override
  public void triggerUsage() {
    RunAnythingUtil.triggerDebuggerStatistics();
  }

  @NotNull
  @Override
  public Component createComponent(boolean isSelected) {
    return RunAnythingUtil.createUndefinedCommandCellRendererComponent(this, isSelected);
  }
}
