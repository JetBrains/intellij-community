
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import org.jetbrains.annotations.NotNull;

public class PreviousOccurenceToolbarAction extends PreviousOccurenceAction {
  private final OccurenceNavigator myNavigator;

  public PreviousOccurenceToolbarAction(OccurenceNavigator navigator) {
    myNavigator = navigator;
    ActionUtil.copyFrom(this, IdeActions.ACTION_PREVIOUS_OCCURENCE);
  }

  @Override
  protected OccurenceNavigator getNavigator(DataContext dataContext) {
    return myNavigator;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return myNavigator.getActionUpdateThread();
  }
}
