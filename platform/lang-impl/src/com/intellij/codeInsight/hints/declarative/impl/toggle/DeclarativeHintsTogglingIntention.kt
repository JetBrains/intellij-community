// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.toggle

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls

class DeclarativeHintsTogglingIntention(
  private val providerId: String,
  private val providerName: @Nls String,
  private val providerEnabledNow: Boolean
) : IntentionAction, LowPriorityAction, DumbAware {
  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun getText(): String {
    val messageKey = if (providerEnabledNow) {
      "inlay.hints.declarative.disable.action.text"
    }
    else {
      "inlay.hints.declarative.enable.action.text"
    }
    return CodeInsightBundle.message(messageKey, providerName)
  }

  override fun getFamilyName(): String {
    return text
  }

  override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
    return true
  }

  override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
    val settings = DeclarativeInlayHintsSettings.getInstance()
    settings.setProviderEnabled(providerId, !providerEnabledNow)
    DeclarativeInlayHintsPassFactory.scheduleRecompute(editor, project)
  }
}