// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.items;

import com.intellij.execution.Executor;
import com.intellij.execution.util.ExecUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.runAnything.RunAnythingAction;
import com.intellij.ide.actions.runAnything.RunAnythingUtil;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Objects;

public class RunAnythingCommandItem extends RunAnythingItem<String> {
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
    RunAnythingUtil.LOG.assertTrue(workDirectory != null);
    RunAnythingUtil.LOG.assertTrue(executor != null);

    RunAnythingUtil.runCommand(workDirectory, myCommandLine, executor, dataContext);
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
    return RunAnythingAction.AD_CONTEXT_TEXT + " , " + RunAnythingAction.AD_DEBUG_TEXT + ", " + RunAnythingAction.AD_DELETE_COMMAND_TEXT;
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
