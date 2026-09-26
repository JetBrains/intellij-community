// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction

internal class ShowTrustProjectDialogAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && !project.isDefault && !TrustedProjects.isProjectTrusted(project)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    if (TrustedProjectsDialog.confirmLoadingUntrustedProject(project)) {
      ApplicationManager.getApplication().messageBus
        .syncPublisher(TrustedProjectsListener.Companion.TOPIC)
        .onProjectTrusted(project)
    }
  }
}