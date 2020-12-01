// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui.actions

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ex.InspectionManagerEx
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ToolWindowEmptyStateAction
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.TextWithMnemonic
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.StatusText
import org.jetbrains.annotations.Nls

class ActivateInspectionResultsToolWindowAction : ToolWindowEmptyStateAction(ToolWindowId.INSPECTION,
                                                                             AllIcons.Toolwindows.ToolWindowInspection) {
  override fun setupEmptyText(project: Project, text: StatusText) {
    val pattern = InspectionsBundle.message("inspections.empty.state.pattern")
    for (fragment in pattern.split("$")) {
      when (fragment) {
        "action_inspect_code" -> appendActionLink(text, project, IdeActions.ACTION_INSPECT_CODE, 
                                                  TextWithMnemonic.parse(ActionsBundle.actionText(IdeActions.ACTION_INSPECT_CODE)).text)
        "action_inspection_by_name" -> appendActionLink(text, project, "RunInspection", 
                                                        TextWithMnemonic.parse(IdeBundle.message("goto.inspection.action.text")).text)
        else -> text.appendText(fragment)
      }
    }
    text.appendLine("")
    text.appendLine(AllIcons.General.ContextHelp, InspectionsBundle.message("inspections.empty.state.help"), SimpleTextAttributes.LINK_ATTRIBUTES) {
      HelpManager.getInstance().invokeHelp("procedures.inspecting.running")
    }
  }

  private fun appendActionLink(text: StatusText, project: Project, actionId: String, @Nls actionText: String) {
    text.appendText(actionText, SimpleTextAttributes.LINK_ATTRIBUTES) {
      val dataContext = FileEditorManager.getInstance(project).selectedEditor?.let { DataManager.getInstance().getDataContext(it.component) }
                        ?: SimpleDataContext.getProjectContext(project)
      val event = AnActionEvent.createFromDataContext(ActionPlaces.UNKNOWN, null, dataContext)
      ActionManager.getInstance().getAction(actionId).actionPerformed(event)
    }
  }

  override fun ensureToolWindowCreated(project: Project) {
    (InspectionManager.getInstance(project) as InspectionManagerEx).contentManager.value
  }
}