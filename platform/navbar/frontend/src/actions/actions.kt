// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.frontend.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil

private const val NAV_BAR_CONTEXT_MENU_GROUP_ID = "NavBarContextMenu"

fun navBarContextMenuActionGroup(): ActionGroup {
  return requireNotNull(ActionUtil.getActionGroup(NAV_BAR_CONTEXT_MENU_GROUP_ID)) {
    "$NAV_BAR_CONTEXT_MENU_GROUP_ID group must be registered"
  }
}
