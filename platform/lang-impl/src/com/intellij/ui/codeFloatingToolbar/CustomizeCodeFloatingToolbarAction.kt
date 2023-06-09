// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.codeFloatingToolbar

import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class CustomizeCodeFloatingToolbarAction: AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val groupName = ActionsBundle.groupText("Floating.CodeToolbar")
    val dialogWrapper = CustomizationUtil.createCustomizeGroupDialog(project, "Floating.CodeToolbar", groupName, null)
    dialogWrapper.show()
  }
}