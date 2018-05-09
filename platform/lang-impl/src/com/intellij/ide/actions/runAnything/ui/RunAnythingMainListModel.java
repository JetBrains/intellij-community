// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.ui;

import com.intellij.ide.actions.runAnything.RunAnythingSearchListModel;
import com.intellij.ide.actions.runAnything.groups.RunAnythingCompletionProviderGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingRecentGroup;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class RunAnythingMainListModel extends RunAnythingSearchListModel {
  @NotNull
  public Collection<RunAnythingGroup> getMainListGroups() {
    Collection<RunAnythingGroup> groups = ContainerUtil.newArrayList(RunAnythingRecentGroup.INSTANCE);
    groups.addAll(RunAnythingCompletionProviderGroup.MAIN_GROUPS);
    return groups;
  }

  @Override
  protected void clearIndexes() {
    RunAnythingGroup.clearIndexes(getMainListGroups());
  }

  @Nullable
  public RunAnythingGroup findGroupByMoreIndex(int index) {
    return RunAnythingGroup.findGroupByMoreIndex(getMainListGroups(), index);
  }

  public void shiftIndexes(int baseIndex, int shift) {
    RunAnythingGroup.shiftIndexes(getMainListGroups(), baseIndex, shift);
  }

  @Nullable
  public String getTitle(int titleIndex) {
    return RunAnythingGroup.getTitle(getMainListGroups(), titleIndex);
  }

  @Override
  public int[] getAllIndexes() {
    RunAnythingGroup.getAllIndexes(getMainListGroups());
    return new int[0];
  }

  @Override
  public boolean isMoreIndex(int index) {
    return RunAnythingGroup.isMoreIndex(getMainListGroups(), index);
  }
}