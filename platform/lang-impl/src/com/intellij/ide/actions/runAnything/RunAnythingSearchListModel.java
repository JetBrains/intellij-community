// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.runAnything;

import com.intellij.ide.actions.runAnything.activity.RunAnythingProvider;
import com.intellij.ide.actions.runAnything.groups.RunAnythingCompletionGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingHelpGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingRecentGroup;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.CollectionListModel;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public abstract class RunAnythingSearchListModel extends CollectionListModel<Object> {
  protected abstract @NotNull List<RunAnythingGroup> getGroups();

  @Nullable
  RunAnythingGroup findGroupByMoreIndex(int index) {
    return RunAnythingGroup.findGroupByMoreIndex(getGroups(), index);
  }

  @Nullable
  RunAnythingGroup findGroupByTitleIndex(int index) {
    return RunAnythingGroup.findGroupByTitleIndex(getGroups(), index);
  }

  void shiftIndexes(int baseIndex, int shift) {
    RunAnythingGroup.shiftIndexes(getGroups(), baseIndex, shift);
  }

  @NlsContexts.PopupTitle @Nullable String getTitle(int titleIndex) {
    return RunAnythingGroup.getTitle(getGroups(), titleIndex);
  }

  int[] getAllIndexes() {
    return RunAnythingGroup.getAllIndexes(getGroups());
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

  public void update() {
    fireContentsChanged(this, 0, getSize() - 1);
  }

  static final class RunAnythingMainListModel extends RunAnythingSearchListModel {
    private final @NotNull List<RunAnythingGroup> myGroups = new ArrayList<>();

    RunAnythingMainListModel() {
      myGroups.add(new RunAnythingRecentGroup());
      myGroups.addAll(RunAnythingCompletionGroup.createCompletionGroups());
    }

    @Override
    public @NotNull List<RunAnythingGroup> getGroups() {
      return myGroups;
    }
  }

  static final class RunAnythingHelpListModel extends RunAnythingSearchListModel {
    private final List<RunAnythingGroup> myGroups;

    RunAnythingHelpListModel() {
      Function<Map.Entry<@Nls String, List<RunAnythingProvider>>, RunAnythingGroup> mapping =
        entry -> new RunAnythingHelpGroup(entry.getKey(), entry.getValue());

      myGroups = ContainerUtil.concat(ContainerUtil.map(StreamEx.of(RunAnythingProvider.EP_NAME.getExtensionList().stream())
                                     .filter(provider -> provider.getHelpGroupTitle() != null)
                                     .groupingBy(provider -> provider.getHelpGroupTitle())
                                     .entrySet(), mapping),
      RunAnythingHelpGroup.EP_NAME.getExtensionList());
    }

    @Override
    protected @NotNull List<RunAnythingGroup> getGroups() {
      return myGroups;
    }
  }
}
