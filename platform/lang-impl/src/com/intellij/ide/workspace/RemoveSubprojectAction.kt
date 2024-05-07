// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.ide.DeleteProvider
import com.intellij.ide.TitledHandler
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext

class SubprojectDeleteProvider(val selected: Collection<Subproject>) : DeleteProvider, TitledHandler {
  override fun deleteElement(dataContext: DataContext) {
    for (subproject in selected) {
      subproject.removeSubproject()
    }
  }

  override fun canDeleteElement(dataContext: DataContext) = selected.isNotEmpty()

  override fun getActionTitle(): String {
    val subproject = selected.singleOrNull()
    if (subproject != null) {
      return ActionsBundle.message("action.remove.workspace.subproject.x.text", subproject.name)
    }
    else {
      return ActionsBundle.message("action.remove.workspace.subprojects.text")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
