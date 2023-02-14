// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.experimental.meetNewUi

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

class MeetNewUiToolWindowFactory : ToolWindowFactory, DumbAware {

  override fun isApplicable(project: Project): Boolean {
    return false // temporary disabled ExperimentalUI.isNewUI()
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val contentManager = toolWindow.contentManager
    val content = contentManager.factory.createContent(MeetNewUiToolWindow(project, toolWindow), null, true)
    contentManager.addContent(content)
  }
}