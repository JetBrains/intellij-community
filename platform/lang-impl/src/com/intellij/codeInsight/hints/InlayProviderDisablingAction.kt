// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("ComponentNotRegistered")

package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.impl.InlayHintsPassFactoryInternal
import com.intellij.codeInsight.hints.settings.showInlaySettings
import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.PSI_FILE
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.Nls.Capitalization.Title
import java.util.function.Supplier

@Suppress("ComponentNotRegistered")
class InlayProviderDisablingAction(
  val name: String,
  val language: Language,
  val project: Project,
  val key: SettingsKey<*>
) : AnAction(CodeInsightBundle.message("disable.inlay.hints.of.type.action.name", name)) {

  override fun actionPerformed(e: AnActionEvent) {
    disableInlayHintsProvider(key, language)
    refreshHints(project)
  }
}

/**
 * Disables given [InlayHintsProvider] for the language.
 * Language is taken from the PSI file in [com.intellij.openapi.actionSystem.DataContext].
 */
internal class DisableInlayHintsProviderAction(
  private val providerKey: SettingsKey<*>,
  private val providerName: Supplier<@Nls(capitalization = Title) String>,
  providerHasCases: Boolean
) : AnAction(
  if (!providerHasCases) Supplier { CodeInsightBundle.message("action.disable.inlay.hints.provider.text", providerName.get()) }
  else Supplier { CodeInsightBundle.message("action.disable.inlay.hints.provider.with.cases.text", providerName.get()) }
) {

  override fun actionPerformed(e: AnActionEvent) {
    val file = e.getData(PSI_FILE) ?: return

    disableInlayHintsProvider(providerKey, file.language)
    refreshHints(file.project)
  }
}

/**
 * Opens settings dialog for the given [InlayHintsProvider].
 * Language for initial selection is taken from the PSI file in [com.intellij.openapi.actionSystem.DataContext].
 */
internal class ConfigureInlayHintsProviderAction(
  private val providerKey: SettingsKey<*>
) : AnAction(CodeInsightBundle.messagePointer("action.configure.inlay.hints.provider.text")) {

  override fun actionPerformed(e: AnActionEvent) {
    val file = e.getData(PSI_FILE) ?: return

    showInlaySettings(file.project, file.language) { it.id == providerKey.id }
  }
}

private fun disableInlayHintsProvider(key: SettingsKey<*>, language: Language) =
  InlayHintsSettings.instance().changeHintTypeStatus(key, language, false)

private fun refreshHints(project: Project) {
  InlayHintsPassFactoryInternal.restartDaemonUpdatingHints(project, "InlayProviderDisablingActionKt.refreshHints")
}