// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.frontend

import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanel
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabVirtualFile

internal class WelcomeScreenLeftTabListener: FileEditorManagerListener {
  override fun selectionChanged(event: FileEditorManagerEvent) {
    if (!Registry.`is`("ide.welcome.screen.change.project.view.depending.on.opened.file", false)) {
      return
    }
    val isChangedToWelcome = event.newFile?.fileType is WelcomeScreenRightTabVirtualFile.WelcomeScreenFileType
    val isChangedFromWelcome = event.oldFile?.fileType is WelcomeScreenRightTabVirtualFile.WelcomeScreenFileType
    val project = event.manager.project
    if (isChangedToWelcome) {
      safeChangeView(project, WelcomeScreenLeftPanel.ID)
    }
    else if (isChangedFromWelcome) {
      safeChangeView(project, ProjectViewPane.ID)
    }
  }

  private fun safeChangeView(project: Project, viewId: String) {
    val canChangeView = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW)?.contentManagerIfCreated != null
    if (!canChangeView) {
      // `ProjectViewImpl.changeView` expects `contentManager` to be not null.
      return
    }

    ProjectViewImpl.getInstance(project).changeView(viewId)
  }
}
