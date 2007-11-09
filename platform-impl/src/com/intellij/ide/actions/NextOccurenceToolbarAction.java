
package com.intellij.ide.actions;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;

public class NextOccurenceToolbarAction extends NextOccurenceAction {
  private OccurenceNavigator myNavigator;

  public NextOccurenceToolbarAction(OccurenceNavigator navigator) {
    myNavigator = navigator;
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_NEXT_OCCURENCE));
  }

  protected OccurenceNavigator getNavigator(DataContext dataContext) {
    return myNavigator;
  }
}
