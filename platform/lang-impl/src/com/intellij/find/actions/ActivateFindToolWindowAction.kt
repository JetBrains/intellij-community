// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.ToolWindowEmptyStateAction
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.ui.SimpleTextAttributes
import com.intellij.usageView.UsageViewContentManager
import com.intellij.util.ui.StatusText
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.ActionListener

@ApiStatus.Internal
class ActivateFindToolWindowAction : ToolWindowEmptyStateAction(ToolWindowId.FIND, AllIcons.Toolwindows.ToolWindowFind) {
  override fun ensureToolWindowCreated(project: Project) {
    UsageViewContentManager.getInstance(project)
  }

  override fun setupEmptyText(project: Project, statusText: StatusText) {
    statusText.isCenterAlignText = false
    statusText.withUnscaledGapAfter(20)
    statusText.clear()

    appendActionRow(
      rowId = 0,
      statusText = statusText,
      title = IdeBundle.message("empty.text.search.everywhere"),
      description = LangBundle.message("status.text.find.toolwindow.empty.state.search.everywhere.description"),
      shortcutText = getSearchEveryWhereShortcutText(),
      listener = createTriggerActionListener(project, IdeActions.ACTION_SEARCH_EVERYWHERE),
    )

    appendActionRow(
      rowId = 1,
      statusText = statusText,
      title = LangBundle.message("status.text.find.toolwindow.empty.state.find.in.files.title"),
      description = LangBundle.message("status.text.find.toolwindow.empty.state.find.in.files.description"),
      shortcutText = getActionShortcutText(IdeActions.ACTION_FIND_IN_PATH),
      listener = createTriggerActionListener(project, IdeActions.ACTION_FIND_IN_PATH),
    )

    appendActionRow(
      rowId = 2,
      statusText = statusText,
      title = LangBundle.message("status.text.find.toolwindow.empty.state.find.usages.title"),
      description = LangBundle.message("status.text.find.toolwindow.empty.state.find.usages.description"),
      shortcutText = getActionShortcutText(IdeActions.ACTION_FIND_USAGES),
    )
  }

  private fun appendActionRow(
    rowId: Int,
    statusText: StatusText,
    @Nls title: String,
    @Nls description: String,
    @Nls shortcutText: String,
    listener: ActionListener? = null,
  ) {
    statusText.appendText(0, rowId, title, if (listener != null) SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES else StatusText.DEFAULT_ATTRIBUTES, listener)
    statusText.appendText(1, rowId, " ", StatusText.DEFAULT_ATTRIBUTES, null)
    statusText.appendText(2, rowId, shortcutText, StatusText.DEFAULT_ATTRIBUTES, null)
    statusText.appendText(3, rowId, " ", StatusText.DEFAULT_ATTRIBUTES, null)
    statusText.appendText(4, rowId, description, StatusText.DEFAULT_ATTRIBUTES, null)
  }

  private fun createTriggerActionListener(project: Project, actionId: String): ActionListener =
    ActionListener {
      val action = ActionManager.getInstance().getAction(actionId)
      val actionEvent = createActionEvent(project, action)
      ActionUtil.invokeAction(action, actionEvent, null)
    }

  private fun createActionEvent(
    project: Project,
    action: AnAction,
  ): AnActionEvent {
    val component = IdeFocusManager.getInstance(project).getFocusOwner()
    val dataContext = SimpleDataContext.builder()
      .add(CommonDataKeys.PROJECT, project)
      .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, component)
      .build()
    return AnActionEvent.createEvent(
      dataContext,
      PresentationFactory().getPresentation(action),
      ActionPlaces.TOOLWINDOW_CONTENT,
      ActionUiKind.NONE,
      null,
    )
  }

  @NlsSafe
  private fun getActionShortcutText(actionId: String): String {
    val shortcut = checkNotNull(ActionManager.getInstance().getKeyboardShortcut(actionId))
    return KeymapUtil.getShortcutText(shortcut)
  }

  /**
   * Duplicated in [com.intellij.openapi.fileEditor.impl.EditorEmptyTextPainter.appendSearchEverywhere]
   */
  @NlsSafe
  private fun getSearchEveryWhereShortcutText(): String {
    val shortcuts = KeymapUtil.getActiveKeymapShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).getShortcuts()
    val message = IdeBundle.message("double.ctrl.or.shift.shortcut", if (SystemInfo.isMac) MacKeymapUtil.SHIFT else "Shift")
    return if (shortcuts.isEmpty()) message else KeymapUtil.getShortcutsText(shortcuts)
  }
}
