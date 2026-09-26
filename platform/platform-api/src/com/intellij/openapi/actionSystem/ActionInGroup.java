// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import org.jetbrains.annotations.NotNull;

public final class ActionInGroup {
  private final DefaultActionGroup group;
  private final AnAction action;

  ActionInGroup(@NotNull DefaultActionGroup group, @NotNull AnAction action) {
    this.group = group;
    this.action = action;
  }

  public @NotNull ActionInGroup setAsSecondary(boolean isSecondary) {
    group.setAsPrimary(action, !isSecondary);
    return this;
  }

  public @NotNull ActionGroup getGroup() {
    return group;
  }
}
