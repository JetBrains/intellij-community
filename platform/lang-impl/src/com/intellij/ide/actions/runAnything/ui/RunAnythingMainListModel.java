// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.ui;

import com.intellij.ide.actions.runAnything.RunAnythingSearchListModel;
import com.intellij.ide.actions.runAnything.groups.RunAnythingCompletionProviderGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingRecentGroup;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class RunAnythingMainListModel extends RunAnythingSearchListModel {
  @NotNull
  @Override
  public Collection<RunAnythingGroup> getGroups() {
    Collection<RunAnythingGroup> groups = ContainerUtil.newArrayList(RunAnythingRecentGroup.INSTANCE);
    groups.addAll(RunAnythingCompletionProviderGroup.MAIN_GROUPS);
    return groups;
  }
}