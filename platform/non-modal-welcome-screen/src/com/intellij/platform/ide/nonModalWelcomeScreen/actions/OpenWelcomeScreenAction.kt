// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.isNonModalWelcomeScreenEnabled
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeRightTabContentProvider
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTab
import com.intellij.platform.ide.progress.runWithModalProgressBlocking

internal class OpenWelcomeScreenAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val isAvailable = WelcomeRightTabContentProvider.getSingleExtension() != null && isNonModalWelcomeScreenEnabled
    e.presentation.isEnabledAndVisible = isAvailable
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    runWithModalProgressBlocking(project, NonModalWelcomeScreenBundle.message("welcome.screen.opening.progress.title")) {
      WelcomeScreenRightTab.show(project)
    }
  }
}
