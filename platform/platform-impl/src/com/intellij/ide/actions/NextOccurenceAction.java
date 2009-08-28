
package com.intellij.ide.actions;

import com.intellij.ide.OccurenceNavigator;

public class NextOccurenceAction extends OccurenceNavigatorActionBase {
  protected String getDescription(OccurenceNavigator navigator) {
    return navigator.getNextOccurenceActionName();
  }

  protected OccurenceNavigator.OccurenceInfo go(OccurenceNavigator navigator) {
    return navigator.goNextOccurence();
  }

  protected boolean hasOccurenceToGo(OccurenceNavigator navigator) {
    return navigator.hasNextOccurence();
  }
}
