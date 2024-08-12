// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything.activity;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectListActionProvider;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.ide.actions.runAnything.RunAnythingContext;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.ide.actions.runAnything.items.RunAnythingItemBase;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.List;

public final class RunAnythingRecentProjectProvider extends RunAnythingAnActionProvider<AnAction> {
  @Override
  public @NotNull Collection<AnAction> getValues(@NotNull DataContext dataContext, @NotNull String pattern) {
    return RecentProjectListActionProvider.getInstance().getActions(false);
  }

  @Override
  public @NotNull RunAnythingItem getMainListItem(@NotNull DataContext dataContext, @NotNull AnAction value) {
    if (value instanceof ReopenProjectAction o) {
      return new RecentProjectElement(o, getCommand(value), value.getTemplatePresentation().getIcon());
    }
    return super.getMainListItem(dataContext, value);
  }

  @Override
  public @NotNull String getCompletionGroupTitle() {
    return IdeBundle.message("run.anything.recent.project.completion.group.title");
  }

  @Override
  public @NotNull String getHelpCommandPlaceholder() {
    return IdeBundle.message("run.anything.recent.project.command.placeholder");
  }

  @Override
  public @NotNull String getHelpCommand() {
    return "open";
  }

  @Override
  public @Nullable String getHelpGroupTitle() {
    return IdeBundle.message("run.anything.recent.project.help.group.title");
  }

  @Override
  public @NotNull String getCommand(@NotNull AnAction value) {
    String projectName = value instanceof ReopenProjectAction o ? o.getProjectNameToDisplay() : value.getTemplatePresentation().getText();
    return getHelpCommand() + " " + ObjectUtils.notNull(projectName, IdeBundle.message("run.anything.actions.undefined"));
  }

  static final class RecentProjectElement extends RunAnythingItemBase {
    private final @NotNull ReopenProjectAction myValue;

    RecentProjectElement(@NotNull ReopenProjectAction value, @NotNull @Nls String command, @Nullable Icon icon) {
      super(command, icon);
      myValue = value;
    }

    @Override
    public @NotNull String getDescription() {
      return FileUtil.toSystemDependentName(myValue.getProjectPath());
    }
  }

  @Override
  public @NotNull List<RunAnythingContext> getExecutionContexts(@NotNull DataContext dataContext) {
    return ContainerUtil.emptyList();
  }
}