// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.newFileDialog.WelcomeScreenNewFileHandler
import com.intellij.platform.ide.nonModalWelcomeScreen.newFileDialog.TemplateName

internal class CreateEmptyFileAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    WelcomeScreenNewFileHandler.showNewFileDialog(
      project = project,
      dialogTitle = NonModalWelcomeScreenBundle.message("welcome.screen.create.file.dialog.title.file"),
      templateName = TemplateName.Static("Generic Empty File")
    )
  }
}
