// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings.language

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.ParameterHintsSettingsPanel
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.psi.PsiFile

class ParameterInlayProviderSettingsModel(
  val provider: InlayParameterHintsProvider,
  language: Language
) : InlayProviderSettingsModel(isParameterHintsEnabledForLanguage(language), ParameterInlayProviderSettingsModel.ID, language) {
  companion object {
    val ID = "parameter.hints.old"
  }

  override val mainCheckBoxLabel: String
    get() = provider.mainCheckboxText
  override val name: String
    get() = CodeInsightBundle.message("settings.inlay.parameter.hints.panel.name")
  override val group: InlayGroup
    get() = InlayGroup.PARAMETERS_GROUP
  override val previewText: String?
    get() = null

  override fun getCasePreview(case: ImmediateConfigurable.Case?): String? {
    return getCasePreview(language, provider, case)
  }

  override fun getCaseDescription(case: ImmediateConfigurable.Case): String? {
    return provider.getProperty("inlay.parameters." + case.id)
  }

  override val component by lazy {
    ParameterHintsSettingsPanel(
      language = language,
      excludeListSupported = provider.isBlackListSupported
    )
  }

  private val optionStates = provider.supportedOptions.map { OptionState(it) }

  override val cases: List<ImmediateConfigurable.Case> = provider.supportedOptions.mapIndexed { index, option ->
    val state = optionStates[index]
    ImmediateConfigurable.Case(option.name,
                               id = option.id,
                               loadFromSettings = { state.state },
                               onUserChanged = { state.state = it },
                               extendedDescription = option.extendedDescriptionSupplier?.get()
    )
  }

  override fun collectAndApply(editor: Editor, file: PsiFile) {
    val pass = ParameterHintsPass(file, editor, HintInfoFilter { true }, true)
    ProgressManager.getInstance().runProcess({
                                               val backup = ParameterInlayProviderSettingsModel(provider, language)
                                               backup.reset()
                                               try {
                                                 apply()
                                                 pass.collectInformation(ProgressIndicatorBase())
                                               }
                                               finally {
                                                 backup.apply()
                                               }
                                             }, DaemonProgressIndicator())
    ApplicationManager.getApplication().invokeLater { pass.applyInformationToEditor() }
  }

  override val description: String?
    get() = null

  override fun toString(): String = name

  override fun apply() {
    setShowParameterHintsForLanguage(isEnabled, language)
    for (state in optionStates) {
      state.apply()
    }
    ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
  }

  override fun isModified(): Boolean {
    if (isEnabled != isParameterHintsEnabledForLanguage(language)) return true
    return optionStates.any { it.isModified() }
  }

  override fun reset() {
    isEnabled = isParameterHintsEnabledForLanguage(language)
    for (state in optionStates) {
      state.reset()
    }
  }

  private data class OptionState(val option: Option, var state: Boolean = option.get()) {
    fun isModified(): Boolean = state != option.get()

    fun reset() {
      state = option.get()
    }

    fun apply() {
      option.set(state)
    }
  }
}