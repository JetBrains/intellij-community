// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.NlsActions.ActionText

class CustomizeCodeFloatingToolbarAction(private val targetGroupActionId: String): AnAction(getActionText()) {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val groupName = ActionManager.getInstance().getAction(targetGroupActionId).templateText
    val dialogWrapper = CustomizationUtil.createCustomizeGroupDialog(project, targetGroupActionId, groupName, null)
    dialogWrapper.show()
  }
}

private fun getActionText(): @ActionText String = ActionsBundle.message("action.Floating.CodeToolbar.Customize.text")