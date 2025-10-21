// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Provides an action to copy the external tool download command to the clipboard.
 */
public class ExternalJavaConfigurationRefreshAction : ExternalJavaConfigurationMissingAction {
  override fun <T> createAction(project: Project, provider: ExternalJavaConfigurationProvider<T>, releaseData: T): AnAction {
    return object : AnAction(JavaBundle.message("external.java.configuration.refresh"), null, AllIcons.Actions.Refresh) {
      override fun actionPerformed(e: AnActionEvent) {
        project.service<ExternalJavaConfigurationService>().updateFromConfig(provider, true)
      }
    }
  }
}
