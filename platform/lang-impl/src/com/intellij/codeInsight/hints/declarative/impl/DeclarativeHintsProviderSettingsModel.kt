// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.ImmediateConfigurable
import com.intellij.codeInsight.hints.InlayDumpUtil
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.util.DeclarativeHintsDumpUtil
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
class DeclarativeHintsProviderSettingsModel(
  private val providerDescription: InlayHintsProviderExtensionBean,
  isEnabled: Boolean,
  language: Language,
  private val project: Project
) : InlayProviderSettingsModel(isEnabled, providerDescription.requiredProviderId(), language) {
  companion object {
    private val PREVIEW_ENTRIES : Key<PreviewEntries> = Key.create("declarative.inlays.preview.entries")
  }

  val provider: InlayHintsProvider
    get() = providerDescription.instance
  private val settings = DeclarativeInlayHintsSettings.getInstance()
  @Suppress("UNCHECKED_CAST")
  private val customSettingsProvider: InlayHintsCustomSettingsProvider<Any?> = (InlayHintsCustomSettingsProvider.getCustomSettingsProvider(id, language) ?: DefaultSettingsProvider()) as InlayHintsCustomSettingsProvider<Any?>
  private var savedSettings = customSettingsProvider.getSettingsCopy()

  private val options: List<MutableOption> = loadOptionsFromSettings()

  private fun loadOptionsFromSettings(): List<MutableOption> = providerDescription.options
    .map {
      val enabledByDefault = it.enabledByDefault
      val enabled = settings.isOptionEnabled(it.requireOptionId(), providerDescription.requiredProviderId()) ?: enabledByDefault
      MutableOption(it, enabled)
    }

  private val _cases: List<ImmediateConfigurable.Case> = options.map { option ->
    ImmediateConfigurable.Case(option.description.getName(providerDescription),
                               option.description.requireOptionId(),
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

  override val description: String?
    get() = providerDescription.getDescription()

  override val previewText: String?
    get() {
      val previewTextWithInlayPlaceholders = DeclarativeHintsPreviewProvider.getPreview(language, id, providerDescription.instance)
      if (previewTextWithInlayPlaceholders == null) return null
      return InlayDumpUtil.removeHints(previewTextWithInlayPlaceholders)
    }

  override fun createFile(project: Project, fileType: FileType, document: Document, caseId: String?): PsiFile {
    val file = super.createFile(project, fileType, document)
    val previewTextWithInlayPlaceholders = if (caseId == null) {
      DeclarativeHintsPreviewProvider.getPreview(language, id, providerDescription.instance)
    } else {
      DeclarativeHintsPreviewProvider.getOptionPreview(language, id, caseId, providerDescription.instance)
    }
    if (previewTextWithInlayPlaceholders != null) {
      val extractedHints = DeclarativeHintsDumpUtil.extractHints(previewTextWithInlayPlaceholders)
      file.putUserData(PREVIEW_ENTRIES, PreviewEntries(caseId, extractedHints))
    }
    return file
  }

  override fun getCasePreview(case: ImmediateConfigurable.Case?): String? {
    if (case == null) return previewText
    val preview = DeclarativeHintsPreviewProvider.getOptionPreview(language, id, case.id, providerDescription.instance)
    if (preview == null) return null
    return InlayDumpUtil.removeHints(preview)
  }

  override fun getCasePreviewLanguage(case: ImmediateConfigurable.Case?): Language = language

  @RequiresBackgroundThread
  override fun collectData(editor: Editor, file: PsiFile): Runnable {
    val providerId = providerDescription.requiredProviderId()

    val enabledOptions = providerDescription.options.associateBy(keySelector = { it.requireOptionId() },
                                                                 valueTransform = { true }) // we enable all the options
    val previewEntries = file.getUserData(PREVIEW_ENTRIES)

    val caseId = previewEntries?.caseId
    val enabled = if (caseId != null) {
      options.find { it.description.optionId == caseId }!!.isEnabled
    }
    else {
      isEnabled
    }

    val pass =
      DeclarativeInlayHintsPass(file, editor, listOf(InlayProviderPassInfo(object : InlayHintsProvider {
      override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
        return object: OwnBypassCollector {
          override fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink) {
            if (previewEntries == null) return
            for ((position, content, hintFormat) in previewEntries.hintInfos) {
              sink.addPresentation(position, hintFormat = hintFormat) {
                text(content)
              }
            }
          }
        }
      }
    }, providerId, enabledOptions)), false, !enabled)

    pass.doCollectInformation(EmptyProgressIndicator())
    return Runnable {
      pass.doApplyInformationToEditor()
    }
  }

  fun collectDataDirectly(editor: Editor, file: PsiFile): Runnable {
    val providerId = providerDescription.requiredProviderId()

    val provider = providerDescription.instance

    val enabledOptions = providerDescription.options.associateBy(keySelector = { it.requireOptionId() },
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
    val option = providerDescription.options.firstOrNull { it.requireOptionId() == caseId } ?: return null

    return option.getDescription(providerDescription)
  }

  override fun apply() {
    for (option in options) {
      settings.setOptionEnabled(option.description.requireOptionId(), id, option.isEnabled)
    }
    settings.setProviderEnabled(id, isEnabled)
    val newSettingsCopy = customSettingsProvider.getSettingsCopy()
    customSettingsProvider.persistSettings(project, newSettingsCopy, language)
    savedSettings = newSettingsCopy
  }

  override fun isModified(): Boolean {
    if (isEnabled != isProviderEnabledInSettings()) {
      return true
    }

    if (customSettingsProvider.isDifferentFrom(project, savedSettings)) return true
    return options.any { it.isEnabled != isOptionEnabledInSettings(it.description) }
  }

  private fun isProviderEnabledInSettings() = settings.isProviderEnabled(providerDescription.requiredProviderId()) ?: providerDescription.isEnabledByDefault

  private fun isOptionEnabledInSettings(option: InlayProviderOption) =
    settings.isOptionEnabled(option.requireOptionId(), id) ?: option.enabledByDefault

  override fun reset() {
    for (option in options) {
      option.isEnabled = (settings.isOptionEnabled(option.description.requireOptionId(), id) ?: option.description.enabledByDefault)
    }
    settings.setProviderEnabled(providerDescription.requiredProviderId(), isProviderEnabledInSettings())
    customSettingsProvider.persistSettings(project, savedSettings, language)
  }

  @Deprecated("Not used in new UI", ReplaceWith("\"\""))
  override val mainCheckBoxLabel: String
    get() = ""

  override val cases: List<ImmediateConfigurable.Case>
    get() = _cases

  private class MutableOption(val description: InlayProviderOption, var isEnabled: Boolean)

  private class PreviewEntries(val caseId: String?, val hintInfos: List<DeclarativeHintsDumpUtil.ExtractedHintInfo>)

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