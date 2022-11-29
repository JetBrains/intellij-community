// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

internal class DisableDeclarativeInlayAction : AnAction(CodeInsightBundle.message("inlay.hints.declarative.disable.action.no.provider.text")) {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    val providerName = e.dataContext.getData(InlayHintsProvider.PROVIDER_NAME)
    if (providerName == null) {
      e.presentation.isEnabledAndVisible = false
      e.presentation.text = CodeInsightBundle.message("inlay.hints.declarative.disable.action.no.provider.text")
      return
    } else {
      e.presentation.isEnabledAndVisible = true
    }
    e.presentation.text = CodeInsightBundle.message("inlay.hints.declarative.disable.action.text", providerName)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val editor = e.getData(CommonDataKeys.EDITOR) ?: return
    val providerId = e.dataContext.getData(InlayHintsProvider.PROVIDER_ID) ?: return
    val settings = DeclarativeInlayHintsSettings.getInstance(project)
    settings.setProviderEnabled(providerId, false)
    DeclarativeInlayHintsPassFactory.scheduleRecompute(editor)
  }
}