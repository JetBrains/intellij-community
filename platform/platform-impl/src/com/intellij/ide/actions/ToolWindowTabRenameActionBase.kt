// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.openapi.wm.impl.content.BaseLabel
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.content.Content
import org.jetbrains.annotations.Nls

open class ToolWindowTabRenameActionBase(val toolWindowId: String, @NlsContexts.Label val labelText: String) : ToolWindowContextMenuActionBase() {
  override fun update(e: AnActionEvent, toolWindow: ToolWindow, selectedContent: Content?) {
    val id = toolWindow.id
    e.presentation.isEnabledAndVisible = e.project != null && id == toolWindowId && selectedContent != null
  }

  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    val contextComponent = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    val tabLabel = if (contextComponent is BaseLabel) contextComponent else e.getData(ToolWindowContentUi.SELECTED_CONTENT_TAB_LABEL)
    val tabLabelContent = tabLabel?.content ?: return
    val project = e.project ?: return

    val initialValue = getContentDisplayNameToEdit(tabLabelContent, project)
    val focusBackComponent = tabLabelContent.preferredFocusableComponent ?: tabLabelContent.component

    RenamePopup(labelText, initialValue) { newName ->
      applyContentDisplayName(tabLabelContent, project, newName)
    }.show(
      anchorComponent = tabLabel,
      disposable = tabLabelContent,
      focusBackComponent = focusBackComponent,
    )
  }

  open fun getContentDisplayNameToEdit(content: Content, project: Project): @NlsContexts.TabTitle String = content.displayName

  open fun applyContentDisplayName(content: Content, project: Project, @Nls newContentName: String) {
    content.displayName = newContentName
  }
}