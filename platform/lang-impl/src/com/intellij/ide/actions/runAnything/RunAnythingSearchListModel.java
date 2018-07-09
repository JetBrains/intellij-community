// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.ide.actions.runAnything.groups.RunAnythingCompletionGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingHelpGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingRecentGroup;
import com.intellij.openapi.project.Project;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.Vector;

@SuppressWarnings("unchecked")
public abstract class RunAnythingSearchListModel extends DefaultListModel {
  @SuppressWarnings("UseOfObsoleteCollectionType")
  Vector myDelegate;

  protected RunAnythingSearchListModel() {
    super();
    myDelegate = ReflectionUtil.getField(DefaultListModel.class, this, Vector.class, "delegate");
    clearIndexes();
  }

  @NotNull
  protected abstract Collection<RunAnythingGroup> getGroups();

  void clearIndexes() {
    RunAnythingGroup.clearIndexes(getGroups());
  }

  @Nullable
  RunAnythingGroup findGroupByMoreIndex(int index) {
    return RunAnythingGroup.findGroupByMoreIndex(getGroups(), index);
  }

  void shiftIndexes(int baseIndex, int shift) {
    RunAnythingGroup.shiftIndexes(getGroups(), baseIndex, shift);
  }

  @Nullable
  String getTitle(int titleIndex) {
    return RunAnythingGroup.getTitle(getGroups(), titleIndex);
  }

  int[] getAllIndexes() {
    RunAnythingGroup.getAllIndexes(getGroups());
    return new int[0];
  }

  boolean isMoreIndex(int index) {
    return RunAnythingGroup.isMoreIndex(getGroups(), index);
  }

  int next(int index) {
    int[] all = getAllIndexes();
    Arrays.sort(all);
    for (int next : all) {
      if (next > index) return next;
    }
    return 0;
  }

  int prev(int index) {
    int[] all = getAllIndexes();
    Arrays.sort(all);
    for (int i = all.length - 1; i >= 0; i--) {
      if (all[i] != -1 && all[i] < index) return all[i];
    }
    return all[all.length - 1];
  }

  @Override
  public void addElement(Object obj) {
    myDelegate.add(obj);
  }

  public void update() {
    fireContentsChanged(this, 0, getSize() - 1);
  }

  public void triggerExecCategoryStatistics(@NotNull Project project, int index) {
    for (int i = index; i >= 0; i--) {
      String title = getTitle(i);
      if (title != null) {
        RunAnythingUsageCollector.Companion
          .trigger(project, getClass().getSimpleName() + ": " + RunAnythingAction.RUN_ANYTHING + " - execution - " + title);
        break;
      }
    }
  }

  public void triggerMoreStatistics(@NotNull Project project, @NotNull RunAnythingGroup group) {
    RunAnythingUsageCollector.Companion
      .trigger(project, getClass().getSimpleName() + ": " + RunAnythingAction.RUN_ANYTHING + " - more - " + group.getTitle());
  }

  public static class RunAnythingMainListModel extends RunAnythingSearchListModel {
    @NotNull
    @Override
    public Collection<RunAnythingGroup> getGroups() {
      Collection<RunAnythingGroup> groups = ContainerUtil.newArrayList(RunAnythingRecentGroup.INSTANCE);
      groups.addAll(RunAnythingCompletionGroup.MAIN_GROUPS);
      return groups;
    }
  }

  public static class RunAnythingHelpListModel extends RunAnythingSearchListModel {
    @NotNull
    @Override
    protected Collection<RunAnythingGroup> getGroups() {
      return Arrays.asList(RunAnythingHelpGroup.EP_NAME.getExtensions());
    }
  }
}
