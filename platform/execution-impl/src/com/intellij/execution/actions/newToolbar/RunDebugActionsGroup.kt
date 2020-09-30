// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.actions.newToolbar

import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator

class RunDebugActionsGroup : ActionGroup() {
  fun actionGroup(): MutableList<AnAction> {
    val actions = mutableListOf<AnAction>()
    CustomActionsSchema.getInstance().getCorrectedAction("RunConfiguration")?.let {
      actions.add(it)
    }
    actions.add(Separator())
    CustomActionsSchema.getInstance().getCorrectedAction("Run")?.let {
      actions.add(it)
    }
    CustomActionsSchema.getInstance().getCorrectedAction("Debug")?.let {
      actions.add(it)
    }


    return actions
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    return actionGroup().toTypedArray()
  }
}