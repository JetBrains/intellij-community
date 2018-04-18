// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.runAnything.RunAnythingCache;
import com.intellij.ide.actions.runAnything.RunAnythingSearchListModel;
import com.intellij.ide.actions.runAnything.items.RunAnythingCommandItem;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RunAnythingCommandGroup extends RunAnythingGroupBase {
  private static final int MAX_COMMANDS = 5;

  @NotNull
  @Override
  public String getTitle() {
    return IdeBundle.message("run.anything.group.title.commands");
  }

  @NotNull
  @Override
  public String getVisibilityKey() {
    return "run.anything.settings.commands";
  }

  @Override
  protected int getMaxInitialItems() {
    return MAX_COMMANDS;
  }

  public SearchResult getItems(@NotNull Project project,
                               @Nullable Module module,
                               @NotNull RunAnythingSearchListModel model,
                               @NotNull String pattern,
                               boolean isInsertionMode,
                               @NotNull Runnable check) {
    SearchResult result = new SearchResult();

    check.run();
    for (String command : ContainerUtil.iterateBackward(RunAnythingCache.getInstance(project).getState().getCommands())) {
      if (addToList(model, result, pattern, new RunAnythingCommandItem(project, module, command), command, isInsertionMode)) break;
      check.run();
    }
    return result;
  }

  @Override
  public boolean shouldBeShownInitially() {
    return true;
  }
}