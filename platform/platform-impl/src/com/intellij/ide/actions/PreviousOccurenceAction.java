// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.OccurenceNavigator;

public class PreviousOccurenceAction extends OccurenceNavigatorActionBase {
  @Override
  protected String getDescription(OccurenceNavigator navigator) {
    return navigator.getPreviousOccurenceActionName();
  }

  @Override
  protected OccurenceNavigator.OccurenceInfo go(OccurenceNavigator navigator) {
    return navigator.goPreviousOccurence();
  }

  @Override
  protected boolean hasOccurenceToGo(OccurenceNavigator navigator) {
    return navigator.hasPreviousOccurence();
  }
}
