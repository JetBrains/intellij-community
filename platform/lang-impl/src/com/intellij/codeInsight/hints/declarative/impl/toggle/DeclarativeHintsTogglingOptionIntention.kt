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

class DeclarativeHintsTogglingOptionIntention(
  private val optionId: String,
  private val providerId: String,
  private val providerName: @Nls String,
  private val optionName: @Nls String,
  private val mode: Mode
) : IntentionAction, LowPriorityAction, DumbAware {
  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun getText(): String {
    return CodeInsightBundle.message(mode.messageKey, optionName, providerName)
  }

  override fun getFamilyName(): String {
    return text
  }

  override fun isAvailable(project: Project, editor: Editor?, psiFile: PsiFile?): Boolean {
    return true
  }

  override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
    val settings = DeclarativeInlayHintsSettings.getInstance()

    when (mode) {
      Mode.EnableProviderAndOption -> {
        settings.setProviderEnabled(providerId, true)
        settings.setOptionEnabled(optionId, providerId, true)
      }
      Mode.EnableOption -> {
        settings.setOptionEnabled(optionId, providerId, true)
      }
      Mode.DisableOption -> {
        settings.setOptionEnabled(optionId, providerId, false)
      }
    }
    DeclarativeInlayHintsPassFactory.scheduleRecompute(editor, project)
  }

  enum class Mode(val messageKey: String) {
    EnableProviderAndOption("inlay.hints.declarative.enable.option.action.text"),
    EnableOption("inlay.hints.declarative.enable.option.action.text"),
    DisableOption("inlay.hints.declarative.disable.option.action.text"),
  }
}