// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

internal sealed class TrustedModeAction(val targetState: Boolean) : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    TrustedProjects.setProjectTrusted(project, targetState)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled = project != null && targetState != TrustedProjects.isProjectTrusted(project)
  }
}

internal class YesTrustAction : TrustedModeAction(true)
internal class NoTrustAction : TrustedModeAction(false)

