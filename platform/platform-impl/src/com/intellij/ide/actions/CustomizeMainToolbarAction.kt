// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomizationUtil
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class CustomizeMainToolbarAction: DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.icon = AllIcons.General.GearPlain
  }

  override fun actionPerformed(e: AnActionEvent) {
    @Suppress("HardCodedStringLiteral")
    val mainToolbarName = CustomActionsSchema.getInstance().getDisplayName(IdeActions.GROUP_MAIN_TOOLBAR_NEW_UI)
                          ?: ActionsBundle.groupText(IdeActions.GROUP_MAIN_TOOLBAR)

    val dialogWrapper = CustomizationUtil.createCustomizeGroupDialog(e.getProject(), IdeActions.GROUP_MAIN_TOOLBAR_NEW_UI, mainToolbarName, null)
    dialogWrapper.show()
  }

  companion object {
    const val ID: String = "CustomizeMainToolbarGroup"
  }
}