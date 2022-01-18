// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.actions

import com.intellij.lang.documentation.ide.impl.DocumentationToolWindowManager
import com.intellij.lang.documentation.ide.ui.isReusable
import com.intellij.lang.documentation.ide.ui.toolWindowUI
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContextMenuActionBase
import com.intellij.ui.content.Content

internal class KeepTabAction : ToolWindowContextMenuActionBase() {

  override fun update(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    e.presentation.isEnabledAndVisible = toolWindow.id == DocumentationToolWindowManager.TOOL_WINDOW_ID
                                         && content != null && content.isReusable
  }

  override fun actionPerformed(e: AnActionEvent, toolWindow: ToolWindow, content: Content?) {
    content?.toolWindowUI?.keep()
  }
}
