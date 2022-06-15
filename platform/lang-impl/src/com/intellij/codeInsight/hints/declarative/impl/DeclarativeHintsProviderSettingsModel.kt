// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import javax.swing.JComponent
import javax.swing.JPanel

class DeclarativeHintsProviderSettingsModel(
  private val providerDescription: InlayHintsProviderExtensionBean,
  isEnabled: Boolean,
  language: Language,
  private val project: Project
) : InlayProviderSettingsModel(isEnabled, providerDescription.requiredProviderId(), language) {
  private val settings = DeclarativeInlayHintsSettings.getInstance(project)
  @Suppress("UNCHECKED_CAST")
  private val customSettingsProvider: InlayHintsCustomSettingsProvider<Any?> = (InlayHintsCustomSettingsProvider.getCustomSettingsProvider(id, language) ?: DefaultSettingsProvider()) as InlayHintsCustomSettingsProvider<Any?>
  private var savedSettings = customSettingsProvider.getSettingsCopy()

  private val options: List<MutableOption> = loadOptionsFromSettings()

  private fun loadOptionsFromSettings(): List<MutableOption> = providerDescription.options
    .map {
      val enabled = settings.isOptionEnabled(it.getOptionId(), providerDescription.requiredProviderId()) ?: it.enabledByDefault
      MutableOption(it, enabled)
    }

  private val _cases: List<ImmediateConfigurable.Case> = options.map { option ->
    ImmediateConfigurable.Case(option.description.getName(providerDescription),
                               option.description.getOptionId(),
                               loadFromSettings = {
                                 option.isEnabled
                               },
                               onUserChanged = { newValue ->
                                 option.isEnabled = newValue
                               },
                               extendedDescription = option.description.getDescription(providerDescription))
  }

  override val group: InlayGroup = providerDescription.requiredGroup()

  override val name: String
    get() = providerDescription.getProviderName()

  override val component: JComponent
    get() = customSettingsProvider.createComponent(project, language)

  override val description: String
    get() = providerDescription.getDescription()

  override val previewText: String?
    get() = DeclarativeHintsPreviewProvider.getPreview(language, id, providerDescription.instance)

  override fun getCasePreview(case: ImmediateConfigurable.Case?): String? {
    if (case == null) return previewText
    return DeclarativeHintsPreviewProvider.getOptionPreview(language, id, case.id, providerDescription.instance)
  }

  override fun getCasePreviewLanguage(case: ImmediateConfigurable.Case?): Language = language

  override fun collectData(editor: Editor, file: PsiFile): Runnable {
    val providerId = providerDescription.requiredProviderId()
    val provider = providerDescription.instance

    val enabledOptions = providerDescription.options.associateBy(keySelector = { it.getOptionId() },
                                                                 valueTransform = { true }) // we enable all the options
    val pass = DeclarativeInlayHintsPassFactory.createPassForPreview(file, editor, provider, providerId, enabledOptions,
                                                                     isDisabled = !isEnabled)
    pass.doCollectInformation(EmptyProgressIndicator())
    return Runnable {
      pass.doApplyInformationToEditor()
    }
  }

  override fun getCaseDescription(case: ImmediateConfigurable.Case): String? {
    val caseId = case.id
    val option = providerDescription.options.firstOrNull { it.getOptionId() == caseId } ?: return null

    return option.getDescription(providerDescription)
  }

  override fun apply() {
    for (option in options) {
      settings.setOptionEnabled(option.description.getOptionId(), id, option.isEnabled)
    }
    settings.setProviderEnabled(id, isEnabled)
    val newSettingsCopy = customSettingsProvider.getSettingsCopy()
    customSettingsProvider.persistSettings(project, newSettingsCopy, language)
    savedSettings = newSettingsCopy
  }

  override fun isModified(): Boolean {
    if (settings.isProviderEnabled(id) != isEnabled) return true
    if (customSettingsProvider.isDifferentFrom(project, savedSettings)) return true
    return options.any { it.isEnabled != (isOptionEnabled(it.description)) }
  }

  private fun isOptionEnabled(option: InlayProviderOption) =
    settings.isOptionEnabled(option.getOptionId(), id) ?: option.enabledByDefault

  override fun reset() {
    for (option in options) {
      option.isEnabled = (settings.isOptionEnabled(option.description.getOptionId(), id) ?: option.description.enabledByDefault)
    }
    customSettingsProvider.persistSettings(project, savedSettings, language)
  }

  @Deprecated("Not used in new UI", ReplaceWith("\"\""))
  override val mainCheckBoxLabel: String
    get() = ""

  override val cases: List<ImmediateConfigurable.Case>
    get() = _cases

  private class MutableOption(val description: InlayProviderOption, var isEnabled: Boolean)

  private class DefaultSettingsProvider : InlayHintsCustomSettingsProvider<Unit> {
    private val component by lazy { JPanel() }
    override fun createComponent(project: Project, language: Language): JComponent = component

    override fun getSettingsCopy() {

    }

    override fun persistSettings(project: Project, settings: Unit, language: Language) {

    }

    override fun putSettings(project: Project, settings: Unit, language: Language) {

    }

    override fun isDifferentFrom(project: Project, settings: Unit): Boolean {
      return false
    }

  }
}