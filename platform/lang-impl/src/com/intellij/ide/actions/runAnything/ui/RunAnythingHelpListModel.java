// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.runAnything.ui;

import com.intellij.ide.actions.runAnything.RunAnythingSearchListModel;
import com.intellij.ide.actions.runAnything.groups.RunAnythingGroup;
import com.intellij.ide.actions.runAnything.groups.RunAnythingHelpGroup;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class RunAnythingHelpListModel extends RunAnythingSearchListModel {
  @NotNull
  @Override
  protected Collection<RunAnythingGroup> getGroups() {
    return Arrays.asList(RunAnythingHelpGroup.EP_NAME.getExtensions());
  }
}