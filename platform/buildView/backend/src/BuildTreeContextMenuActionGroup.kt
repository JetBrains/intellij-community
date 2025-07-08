// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.backend

import com.intellij.build.BuildTreeConsoleView
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

private class BuildTreeContextMenuActionGroup : ActionGroup() {
  override fun getChildren(e: AnActionEvent?): Array<out AnAction> {
    val view = e?.getData(BuildTreeConsoleView.COMPONENT_KEY) ?: return emptyArray()
    return view.getContextMenuGroup(e.dataContext).getChildren(e)
  }
}