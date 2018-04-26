// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything;

import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.util.ReflectionUtil;

import javax.swing.*;
import java.util.Arrays;
import java.util.Vector;

@SuppressWarnings("unchecked")
public class RunAnythingSearchListModel extends DefaultListModel {
  @SuppressWarnings("UseOfObsoleteCollectionType")
  Vector myDelegate;

  RunAnythingSearchListModel() {
    super();
    myDelegate = ReflectionUtil.getField(DefaultListModel.class, this, Vector.class, "delegate");
    RunAnythingGroup.clearIndexes();
  }

  int next(int index) {
    int[] all = RunAnythingGroup.getAllIndexes();
    Arrays.sort(all);
    for (int next : all) {
      if (next > index) return next;
    }
    return 0;
  }

  int prev(int index) {
    int[] all = RunAnythingGroup.getAllIndexes();
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
}
