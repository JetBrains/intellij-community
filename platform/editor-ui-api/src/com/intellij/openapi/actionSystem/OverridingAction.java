// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.Nullable;

/**
 * Action intended to replace an existing action using the "overrides" attribute in plugin.xml and receiving an instance of the action
 * being overridden.
 */
public abstract class OverridingAction extends AnAction {
  /**
   * The instance of the action which has been overridden by this action.
   */
  @Nullable
  protected AnAction myBaseAction;

  @Nullable
  public AnAction getBaseAction() {
    return myBaseAction;
  }

  public void setBaseAction(@Nullable AnAction baseAction) {
    myBaseAction = baseAction;
  }
}
