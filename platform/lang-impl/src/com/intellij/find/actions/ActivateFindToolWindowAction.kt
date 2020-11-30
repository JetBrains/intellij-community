// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ToolWindowEmptyStateAction
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.usageView.UsageViewContentManager
import com.intellij.util.ui.StatusText

class ActivateFindToolWindowAction : ToolWindowEmptyStateAction(ToolWindowId.FIND, AllIcons.Toolwindows.ToolWindowFind) {
  override fun ensureToolWindowCreated(project: Project) {
    UsageViewContentManager.getInstance(project)
  }

  override fun setupEmptyText(project: Project, statusText: StatusText) {
    appendWithActionShortcut(statusText, "status.text.find.toolwindow.empty.state.1", IdeActions.ACTION_FIND_USAGES)
    statusText.appendLine("")
    appendWithActionShortcut(statusText, "status.text.find.toolwindow.empty.state.2", IdeActions.ACTION_FIND_IN_PATH)
  }

  private fun appendWithActionShortcut(statusText: StatusText, key: String, actionId: String) {
    val shortcut = ActionManager.getInstance().getKeyboardShortcut(actionId)
    val shortcutText = shortcut?.let { " (${KeymapUtil.getShortcutText(shortcut)})" } ?: ""
    statusText.appendLine(LangBundle.message(key, shortcutText));
  }
}
