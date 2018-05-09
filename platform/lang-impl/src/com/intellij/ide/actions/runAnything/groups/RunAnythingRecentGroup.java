// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.groups;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.runAnything.RunAnythingCache;
import com.intellij.ide.actions.runAnything.activity.RunAnythingActivityProvider;
import com.intellij.ide.actions.runAnything.activity.RunAnythingRecentProvider;
import com.intellij.ide.actions.runAnything.items.RunAnythingItem;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class RunAnythingRecentGroup extends RunAnythingGroupBase {
  public static final RunAnythingRecentGroup INSTANCE = new RunAnythingRecentGroup();

  private RunAnythingRecentGroup() {}

  @NotNull
  @Override
  public String getTitle() {
    return IdeBundle.message("run.anything.recent.group.title");
  }

  @NotNull
  @Override
  public Collection<RunAnythingItem> getGroupItems(@NotNull DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    assert project != null;

    Collection<RunAnythingItem> collector = ContainerUtil.newArrayList();
    for (String command : ContainerUtil.iterateBackward(RunAnythingCache.getInstance(project).getState().getCommands())) {
      for (RunAnythingRecentProvider provider : getProviders()) {
        Object matchingValue = provider.findMatchingValue(dataContext, command);
        if (matchingValue != null) {
          //noinspection unchecked
          collector.add(provider.getMainListItem(dataContext, matchingValue));
          break;
        }
      }
    }

    return collector;
  }

  private static List<RunAnythingRecentProvider> getProviders() {
    return StreamEx.of(RunAnythingActivityProvider.EP_NAME.getExtensions())
                   .select(RunAnythingRecentProvider.class)
                   .collect(Collectors.toList());
  }

  @Override
  protected int getMaxInitialItems() {
    return 15;
  }
}