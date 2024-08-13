// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInManager
import com.intellij.ide.SelectInTarget
import com.intellij.ide.actions.SelectInContextImpl
import com.intellij.ide.impl.ProjectViewSelectInGroupTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.PlatformDataKeys.TOOL_WINDOW
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowId.PROJECT_VIEW
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.ui.ComponentUtil

private const val SELECT_CONTEXT_FILE = "SelectInProjectView"
private const val SELECT_OPENED_FILE = "SelectOpenedFileInProjectView"

internal class SelectFileAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun actionPerformed(event: AnActionEvent) {
    when (getActionId(event)) {
      SELECT_CONTEXT_FILE -> getSelector(event)?.run { target.selectIn(context, true) }
      SELECT_OPENED_FILE ->
        if (Registry.`is`("ide.selectIn.works.as.revealIn.when.project.view.focused")) {
          ActionManager.getInstance().getAction("RevealIn")?.actionPerformed(event)
        } else {
          if (shouldUseLastFocusedEditor(event)) {
            getView(event)?.selectOpenedFileUsingLastFocusedEditor()
          }
          else {
            getView(event)?.selectOpenedFile()
          }
        }
    }
  }

  private fun shouldUseLastFocusedEditor(event: AnActionEvent): Boolean {
    // When the action is invoked by clicking the button in the tool window title,
    // it's tricky to figure out which file to select:
    // - If the project view is docked to the IDE frame, it feels more natural that the active editor
    //   in the same frame is used;
    // - but if a detached editor was just active, and the IDE frame became focused
    //   by the same mouse click that invoked this action, then it feels more natural
    //   that that detached editor should be used instead;
    // - if, on the other hand, the project view is windowed or floating, then
    //   it always makes sense to use the last focused editor.
    if (event.place != ActionPlaces.TOOLWINDOW_TITLE) return false // All of this makes sense only for the tool window button click.
    val projectFrame = WindowManagerEx.getInstanceEx().getFrame(event.project)
    val projectViewFrame = ComponentUtil.getWindow(event.dataContext.getData(PlatformDataKeys.CONTEXT_COMPONENT))
    val docked = projectFrame != null && projectFrame == projectViewFrame
    if (!docked) {
      if (SELECT_IN_LOG.isDebugEnabled) {
        SELECT_IN_LOG.debug("Forcing use of the last focused editor because the project view is detached")
      }
      return true
    }
    val wasJustActivated = projectFrame?.wasJustActivatedByClick == true
    if (wasJustActivated) {
      if (SELECT_IN_LOG.isDebugEnabled) {
        SELECT_IN_LOG.debug("Forcing use of the last focused editor because the IDE frame was activated " +
         "by pressing the Select Opened File button")
      }
      return true
    }
    return false
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
        event.presentation.isVisible = view?.isSelectOpenedFileEnabled == true
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

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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

val SELECT_IN_LOG = logger<SelectInProjectViewImpl>()
