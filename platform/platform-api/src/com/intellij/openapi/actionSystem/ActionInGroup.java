// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;

public class ActionInGroup {
  private final DefaultActionGroup myGroup;
  private final AnAction myAction;

  ActionInGroup(@NotNull DefaultActionGroup group, @NotNull AnAction action) {
    myGroup = group;
    myAction = action;
  }

  public @NotNull ActionInGroup setAsSecondary(boolean isSecondary) {
    myGroup.setAsPrimary(myAction, !isSecondary);
    return this;
  }

  public @NotNull ActionGroup getGroup() {
    return myGroup;
  }
}
