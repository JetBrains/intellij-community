// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTab
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

internal class OpenWelcomeScreenAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    runWithModalProgressBlocking(project, NonModalWelcomeScreenBundle.message("welcome.screen.opening.progress.title")) {
      WelcomeScreenRightTab.show(project)
    }
  }
}
