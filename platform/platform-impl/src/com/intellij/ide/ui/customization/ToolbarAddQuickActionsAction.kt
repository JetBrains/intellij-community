// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization


import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.mac.touchbar.TouchbarSupport

abstract class ToolbarAddQuickActionsAction(protected val actionIds: List<String>, protected val rootGroupID: String, protected val insertStrategy: ToolbarQuickActionInsertStrategy): AnAction() {

  override fun actionPerformed(e: AnActionEvent) {
    val schema = CustomActionsSchema()
    schema.copyFrom(CustomActionsSchema.getInstance())
    insertStrategy.addToSchema(actionIds, schema)
    CustomActionsSchema.getInstance().copyFrom(schema)

    CustomActionsSchema.getInstance().initActionIcons()
    CustomActionsSchema.setCustomizationSchemaForCurrentProjects()
    if (SystemInfo.isMac) {
      TouchbarSupport.reloadAllActions()
    }
    CustomActionsListener.fireSchemaChanged()
  }

  override fun update(e: AnActionEvent) {
    val schema = CustomActionsSchema.getInstance()
    e.presentation.isEnabledAndVisible = actionIds.none { id -> groupContainsAction(rootGroupID, id, schema)}
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}