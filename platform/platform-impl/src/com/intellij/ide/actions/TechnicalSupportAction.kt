// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.SendFeedbackAction.Companion.getDescription
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.ide.customization.ExternalProductResourceUrls.Companion.getInstance
import kotlinx.coroutines.launch

class TechnicalSupportAction : AnAction(), DumbAware {
  override fun update(e: AnActionEvent) {
    e.presentation.setVisible(getInstance().technicalSupportUrl != null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun actionPerformed(e: AnActionEvent) {
    val urlFunction = getInstance().technicalSupportUrl
    if (urlFunction == null) return
    val project = e.project
    service<ReportFeedbackService>().coroutineScope.launch {
      BrowserUtil.browse(urlFunction.invoke(getDescription(project)).toExternalForm(), project)
    }
  }
}
