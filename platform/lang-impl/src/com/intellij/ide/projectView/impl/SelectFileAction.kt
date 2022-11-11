// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.ide.SelectInTarget
import com.intellij.ide.actions.SelectInContextImpl
import com.intellij.ide.impl.ProjectViewSelectInGroupTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys.TOOL_WINDOW
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowId.PROJECT_VIEW

private const val SELECT_CONTEXT_FILE = "SelectInProjectView"
private const val SELECT_OPENED_FILE = "SelectOpenedFileInProjectView"

internal class SelectFileAction : DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    when (getActionId(event)) {
      SELECT_CONTEXT_FILE -> getSelector(event)?.run { target.selectIn(context, true) }
      SELECT_OPENED_FILE ->
        if (Registry.`is`("ide.selectIn.works.as.revealIn.when.project.view.focused")) {
          ActionManager.getInstance().getAction("RevealIn")?.actionPerformed(event)
        } else {
          getView(event)?.selectOpenedFile?.run()
        }
    }
  }

  override fun update(event: AnActionEvent) {
    val id = getActionId(event)
    event.presentation.text = ActionsBundle.actionText(id)
    event.presentation.description = ActionsBundle.actionDescription(id)
    when (id) {
      SELECT_CONTEXT_FILE -> {
        event.presentation.isEnabledAndVisible = getSelector(event)?.run { target.canSelect(context) } == true
      }
      SELECT_OPENED_FILE -> {
        val view = getView(event)
        event.presentation.isEnabled = event.getData(PlatformDataKeys.LAST_ACTIVE_FILE_EDITOR) != null
        event.presentation.isVisible = Utils.getOrCreateUpdateSession(event).compute(this, "isSelectEnabled",
                                                                                     ActionUpdateThread.EDT) { view?.isSelectOpenedFileEnabled == true }
        event.project?.let { project ->
          if (event.presentation.isVisible && getFirstKeyboardShortcutText(id).isEmpty()) {
            val shortcut = getFirstKeyboardShortcutText("SelectIn")
            if (shortcut.isNotEmpty()) {
              val index = 1 + SelectInManager.getInstance(project).targetList.indexOfFirst { it is ProjectViewSelectInGroupTarget }
              if (index >= 1) event.presentation.text = "${event.presentation.text} ($shortcut, $index)"
            }
          }
        }
      }
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  private data class Selector(val target: SelectInTarget, val context: SelectInContext)

  private fun getSelector(event: AnActionEvent): Selector? {
    val target = SelectInManager.findSelectInTarget(PROJECT_VIEW, event.project) ?: return null
    val context = SelectInContextImpl.createContext(event) ?: return null
    return Selector(target, context)
  }

  private fun getView(event: AnActionEvent) =
    event.project?.let { ProjectView.getInstance(it) as? ProjectViewImpl }

  private fun getActionId(event: AnActionEvent) =
    when (event.getData(TOOL_WINDOW)?.id) {
      PROJECT_VIEW -> SELECT_OPENED_FILE
      else -> SELECT_CONTEXT_FILE
    }
}
