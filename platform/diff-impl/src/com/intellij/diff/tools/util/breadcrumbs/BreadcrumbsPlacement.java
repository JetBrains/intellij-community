// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.breadcrumbs;

import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.annotations.NotNull;

public enum BreadcrumbsPlacement {
  TOP(IdeActions.BREADCRUMBS_SHOW_ABOVE),
  BOTTOM(IdeActions.BREADCRUMBS_SHOW_BELOW),
  HIDDEN(IdeActions.BREADCRUMBS_HIDE_BOTH);

  @NotNull private final String myActionId;

  BreadcrumbsPlacement(@NotNull String actionId) {
    myActionId = actionId;
  }

  @NotNull
  public String getActionId() {
    return myActionId;
  }
}
