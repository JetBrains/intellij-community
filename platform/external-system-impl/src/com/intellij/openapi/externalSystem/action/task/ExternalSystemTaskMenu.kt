// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.action.task

import com.intellij.execution.Executor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.SimpleToolWindowPanel

internal class ExternalSystemTaskMenu : DefaultActionGroup(), DumbAware {

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return super.getChildren(e)

    return Executor.EXECUTOR_EXTENSION_NAME.extensionList
      .filter { it.isApplicable(project) }
      .map { e.actionManager.getAction(it.contextActionId) }
      .toTypedArray() + super.getChildren(e)
  }


  internal class Listener : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
      val view = event.getData(ExternalSystemDataKeys.VIEW) ?: return
      if ((view as SimpleToolWindowPanel).name != event.place) return
      val actionId = event.actionManager.getId(action) ?: return
      val executor = Executor.EXECUTOR_EXTENSION_NAME.extensionList.find { it.contextActionId == actionId} ?: return

      val extActionId = ExternalSystemActionsCollector.ActionId.RunExternalSystemTaskAction
      ExternalSystemActionsCollector.trigger(view.project, view.systemId, extActionId, event, executor)
    }
  }
}