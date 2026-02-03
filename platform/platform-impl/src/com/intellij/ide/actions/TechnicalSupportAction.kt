// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.BrowserUtil
import com.intellij.ide.ConsentOptionsProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.ide.customization.ExternalProductResourceUrls
import com.intellij.util.PlatformUtils
import com.intellij.util.Url
import kotlinx.coroutines.launch

internal class TechnicalSupportAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.setEnabledAndVisible(getTechnicalSupportUrl() != null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val url = getTechnicalSupportUrl() ?: return
    val project = e.project
    service<ReportFeedbackService>().coroutineScope.launch {
      BrowserUtil.browse(url(SendFeedbackAction.getDescription(project)).toExternalForm(), project)
    }
  }

  private fun getTechnicalSupportUrl(): ((description: String) -> Url)? {
    val isPaid = PlatformUtils.isCommercialEdition() &&
                 !service<ConsentOptionsProvider>().isActivatedWithFreeLicense
    return if (isPaid) {
      ExternalProductResourceUrls.getInstance().technicalSupportUrl
    } else {
      ExternalProductResourceUrls.getInstance().freeTechnicalSupportUrl
    }
  }
}
