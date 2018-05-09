// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.ui;

import com.intellij.ide.actions.runAnything.RunAnythingSearchListModel;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import org.jetbrains.annotations.Nullable;

import static com.intellij.ide.actions.runAnything.groups.RunAnythingHelpGroup.HELP_GROUPS;

public class RunAnythingHelpListModel extends RunAnythingSearchListModel {
  @Override
  public void clearIndexes() {
    RunAnythingGroup.clearIndexes(HELP_GROUPS);
  }

  @Nullable
  public RunAnythingGroup findGroupByMoreIndex(int index) {
    return RunAnythingGroup.findGroupByMoreIndex(HELP_GROUPS, index);
  }

  public void shiftIndexes(int baseIndex, int shift) {
    RunAnythingGroup.shiftIndexes(HELP_GROUPS, baseIndex, shift);
  }

  @Nullable
  public String getTitle(int titleIndex) {
    return RunAnythingGroup.getTitle(HELP_GROUPS, titleIndex);
  }

  @Override
  public int[] getAllIndexes() {
    return RunAnythingGroup.getAllIndexes(HELP_GROUPS);
  }

  @Override
  public boolean isMoreIndex(int index) {
    return RunAnythingGroup.isMoreIndex(HELP_GROUPS, index);
  }
}