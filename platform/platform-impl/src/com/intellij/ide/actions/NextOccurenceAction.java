// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.OccurenceNavigator;

public class NextOccurenceAction extends OccurenceNavigatorActionBase {
  @Override
  protected String getDescription(OccurenceNavigator navigator) {
    return navigator.getNextOccurenceActionName();
  }

  @Override
  protected OccurenceNavigator.OccurenceInfo go(OccurenceNavigator navigator) {
    return navigator.goNextOccurence();
  }

  @Override
  protected boolean hasOccurenceToGo(OccurenceNavigator navigator) {
    return navigator.hasNextOccurence();
  }
}
