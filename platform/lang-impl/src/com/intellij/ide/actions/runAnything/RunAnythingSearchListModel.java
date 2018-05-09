// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.openapi.project.Project;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
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

  protected abstract void clearIndexes();

  @Nullable
  protected abstract RunAnythingGroup findGroupByMoreIndex(int index);

  protected abstract void shiftIndexes(int baseIndex, int shift);

  @Nullable
  protected abstract String getTitle(int titleIndex);

  protected abstract int[] getAllIndexes();

  protected abstract boolean isMoreIndex(int index);

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
}
