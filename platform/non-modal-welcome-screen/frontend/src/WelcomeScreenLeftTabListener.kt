// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.frontend

import com.intellij.ide.projectView.impl.ProjectViewImpl
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.WelcomeScreenLeftPanel.Companion.ID
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
      ProjectViewImpl.getInstance(project).changeView(ID)
    }
    else if (isChangedFromWelcome) {
      ProjectViewImpl.getInstance(project).changeView(ProjectViewPane.ID)
    }
  }
}
