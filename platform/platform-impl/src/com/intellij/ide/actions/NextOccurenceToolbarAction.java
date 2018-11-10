
// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;

public class NextOccurenceToolbarAction extends NextOccurenceAction {
  private final OccurenceNavigator myNavigator;

  public NextOccurenceToolbarAction(OccurenceNavigator navigator) {
    myNavigator = navigator;
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_OCCURENCE));
  }

  @Override
  protected OccurenceNavigator getNavigator(DataContext dataContext) {
    return myNavigator;
  }
}
