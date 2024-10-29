// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.VirtualFile

internal class ParseSdkmanrcAction: AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val sdkmanrcConfigProvider = ExternalJavaConfigurationProvider.EP_NAME.extensionList.find { it is SdkmanrcConfigurationProvider }

    if (sdkmanrcConfigProvider == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val file: VirtualFile? = CommonDataKeys.VIRTUAL_FILE.getData(e.dataContext)

    e.presentation.isEnabledAndVisible = file != null && file.path == sdkmanrcConfigProvider.getConfigurationFile(project).absolutePath
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val sdkmanrcConfigProvider = ExternalJavaConfigurationProvider.EP_NAME.extensionList.find { it is SdkmanrcConfigurationProvider } ?: return
    project.service<ExternalJavaConfigurationService>().updateJdkFromConfig(sdkmanrcConfigProvider)
  }
}