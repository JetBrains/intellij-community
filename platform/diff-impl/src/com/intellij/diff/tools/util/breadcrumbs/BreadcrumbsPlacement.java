// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.breadcrumbs;

import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NotNull;

public enum BreadcrumbsPlacement {
  TOP(IdeActions.BREADCRUMBS_SHOW_ABOVE),
  BOTTOM(IdeActions.BREADCRUMBS_SHOW_BELOW),
  HIDDEN(IdeActions.BREADCRUMBS_HIDE_BOTH);

  private final @NotNull String myActionId;

  BreadcrumbsPlacement(@NotNull String actionId) {
    myActionId = actionId;
  }

  public @NotNull String getActionId() {
    return myActionId;
  }
}
