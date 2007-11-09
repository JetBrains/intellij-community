
package com.intellij.ide.actions;

import com.intellij.ide.OccurenceNavigator;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.IdeActions;

public class PreviousOccurenceToolbarAction extends PreviousOccurenceAction {
  private OccurenceNavigator myNavigator;

  public PreviousOccurenceToolbarAction(OccurenceNavigator navigator) {
    myNavigator = navigator;
    copyFrom(ActionManager.getInstance().getAction(IdeActions.ACTION_PREVIOUS_OCCURENCE));
  }

  protected OccurenceNavigator getNavigator(DataContext dataContext) {
    return myNavigator;
  }
}
