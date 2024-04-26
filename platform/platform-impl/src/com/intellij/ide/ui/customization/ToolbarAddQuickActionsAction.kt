// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.touchbar.TouchbarSupport

class ToolbarAddQuickActionsAction(private val info: ToolbarAddQuickActionInfo) : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun actionPerformed(e: AnActionEvent) {
    val schema = CustomActionsSchema(null)
    val customActionSchema = CustomActionsSchema.getInstance()
    schema.copyFrom(customActionSchema)
    info.insertStrategy.addActions(info.actionIDs, schema)
    customActionSchema.copyFrom(schema)

    customActionSchema.initActionIcons()
    customActionSchema.setCustomizationSchemaForCurrentProjects()
    if (SystemInfoRt.isMac) {
      TouchbarSupport.reloadAllActions()
    }
    CustomActionsListener.fireSchemaChanged()
  }

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.text = info.name
    presentation.icon = info.icon
    val schema = CustomActionsSchema.getInstance()
    presentation.isEnabledAndVisible = info.actionIDs.none { id -> info.insertStrategy.checkExists(id, schema) }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}