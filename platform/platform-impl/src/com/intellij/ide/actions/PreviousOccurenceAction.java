
package com.intellij.ide.actions;

import com.intellij.ide.OccurenceNavigator;

public class PreviousOccurenceAction extends OccurenceNavigatorActionBase {
  protected String getDescription(OccurenceNavigator navigator) {
    return navigator.getPreviousOccurenceActionName();
  }

  protected OccurenceNavigator.OccurenceInfo go(OccurenceNavigator navigator) {
    return navigator.goPreviousOccurence();
  }

  protected boolean hasOccurenceToGo(OccurenceNavigator navigator) {
    return navigator.hasPreviousOccurence();
  }
}
