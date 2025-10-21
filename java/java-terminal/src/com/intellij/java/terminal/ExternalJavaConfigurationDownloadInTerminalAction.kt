// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.impl.ExternalJavaConfigurationMissingAction
import com.intellij.openapi.projectRoots.impl.ExternalJavaConfigurationProvider
import com.intellij.openapi.projectRoots.impl.ExternalJavaConfigurationService
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * Provides an action to open a new terminal session and execute the download command.
 */
class ExternalJavaConfigurationDownloadInTerminalAction : ExternalJavaConfigurationMissingAction {
  override fun <T> createAction(project: Project, provider: ExternalJavaConfigurationProvider<T>, releaseData: T): AnAction? {
    val command = provider.getDownloadCommandFor(releaseData) ?: return null
    return object : AnAction(JavaTerminalBundle.message("external.java.configuration.run.command", command), null, AllIcons.Actions.Download) {
      override fun actionPerformed(e: AnActionEvent) {
        val session = TerminalToolWindowManager.getInstance(project).createNewSession()
        val service = project.service<ExternalJavaConfigurationService>()
        service.addTerminationCallback(session, provider)
        session.sendCommandToExecute(command)
      }
    }
  }
}
