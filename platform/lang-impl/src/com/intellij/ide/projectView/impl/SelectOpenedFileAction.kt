// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl

import com.intellij.ide.SelectInManager
import com.intellij.ide.impl.ProjectViewSelectInGroupTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.idea.ActionsBundle.actionText
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.keymap.KeymapUtil.getFirstKeyboardShortcutText
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions.ActionText

private const val ID = "SelectOpenedFileInProjectView"

internal class SelectOpenedFileAction : DumbAwareAction() {
  override fun actionPerformed(event: AnActionEvent) {
    getView(event)?.performSelectOpenedFile()
  }

  override fun update(event: AnActionEvent) {
    val enabled = true == getView(event)?.isSelectOpenedFileEnabled
    event.presentation.isEnabledAndVisible = enabled
    event.presentation.text = getText(event, enabled)
  }

  private fun getView(event: AnActionEvent) = event.project?.let { ProjectView.getInstance(it) } as? ProjectViewImpl

  @ActionText
  private fun getText(event: AnActionEvent, enabled: Boolean): String {
    val text = actionText(ID)
    if (!enabled) return text

    val project = event.project ?: return text
    if (getFirstKeyboardShortcutText(ID).isNotEmpty()) return text

    val shortcut = getFirstKeyboardShortcutText("SelectIn")
    if (shortcut.isEmpty()) return text

    val index = 1 + SelectInManager.getInstance(project).targetList.indexOfFirst { it is ProjectViewSelectInGroupTarget }
    if (index < 1) return text

    return "$text ($shortcut, $index)"
  }
}
