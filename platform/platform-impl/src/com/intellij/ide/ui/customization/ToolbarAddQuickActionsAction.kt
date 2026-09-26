// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization

import com.intellij.internal.statistic.collectors.fus.actions.persistence.ActionsEventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.mac.touchbar.TouchbarSupport
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ToolbarAddQuickActionsAction(private val info: ToolbarAddQuickActionInfo) : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {

  override fun actionPerformed(e: AnActionEvent) {
    AddQuickActionsCollector.reportActionAdded(e.project, info.actionIDs.first(), e.place)
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

private object AddQuickActionsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("toolbar.add.quick.action", 1)
  private val addedEvent = GROUP.registerEvent("action.added", ActionsEventLogGroup.ACTION_ID, EventFields.ActionPlace)
  fun reportActionAdded(project: Project?, actionId: String, place: String) = addedEvent.log(project, actionId, place)
  override fun getGroup(): EventLogGroup = GROUP
}