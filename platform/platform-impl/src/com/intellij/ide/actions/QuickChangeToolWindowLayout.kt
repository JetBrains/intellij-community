// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project

class QuickChangeToolWindowLayout : QuickSwitchSchemeAction() {

  override fun fillActions(project: Project?, group: DefaultActionGroup, dataContext: DataContext) {
    val sourceGroup = ActionManager.getInstance().getAction(RestoreNamedLayoutActionGroup.ID) as ActionGroup
    sourceGroup.getChildren(null).forEach { group.add(it) }
  }

}
