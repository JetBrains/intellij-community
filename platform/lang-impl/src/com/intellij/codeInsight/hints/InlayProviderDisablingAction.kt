// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.settings.InlayHintsConfigurable
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

class InlayProviderDisablingAction(
  val name: String,
  val language: Language,
  val project: Project,
  val key: SettingsKey<*>
) : AnAction(CodeInsightBundle.message("disable.inlay.hints.of.type.action.name", name)) {
  override fun actionPerformed(e: AnActionEvent) {
    val settings = InlayHintsSettings.instance()
    settings.changeHintTypeStatus(key, language, false)
    InlayHintsPassFactory.forceHintsUpdateOnNextPass()
    InlayHintsConfigurable.updateInlayHintsUI()
  }
}