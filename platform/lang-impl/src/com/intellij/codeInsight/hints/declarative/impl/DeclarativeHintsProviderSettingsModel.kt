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
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
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

  private val _cases: List<ImmediateConfigurable.Case> = options
    .filter { it.description.showInTree }
    .map { option ->
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
    get() = panel {
      options.filter { !it.description.showInTree }.forEach { opt ->
        row {
          checkBox(opt.description.getName(providerDescription))
            .selected(opt.isEnabled)
            .onChanged {
              opt.isEnabled = it.isSelected
              onChangeListener!!.settingsChanged()
            }
            .component.toolTipText = opt.description.getDescription(providerDescription)
        }
      }
      row {
        cell(customSettingsProvider.createComponent(project, language))
      }
    }

  override val description: String?
    get() = providerDescription.getDescription()

  override val previewText: String?
    get() {
      val previewTextWithInlayPlaceholders = DeclarativeHintsPreviewProvider.getPreview(language, id, providerDescription.instance)
      if (previewTextWithInlayPlaceholders == null) return null
      return InlayDumpUtil.removeInlays(previewTextWithInlayPlaceholders)
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
    return InlayDumpUtil.removeInlays(preview)
  }

  override fun getCasePreviewLanguage(case: ImmediateConfigurable.Case?): Language = language

  @RequiresBackgroundThread
  override fun collectData(editor: Editor, file: PsiFile): Runnable {
    val previewEntries = file.getUserData(PREVIEW_ENTRIES)
    if (previewEntries == null) return EmptyRunnable.getInstance()
    return collectFromExtractedHints(editor, file, previewEntries)
  }

  private fun collectFromExtractedHints(editor: Editor, file: PsiFile, previewEntries: PreviewEntries): Runnable {
    val sourceId = "preview.extractedHints"
    val sink = PreviewInlayTreeSink(providerDescription, isEnabled, options, sourceId)
    val caseId = previewEntries.caseId
    if (caseId != null) {
      val opt = options.findOrError(caseId, providerDescription)
      sink.startOption(opt)
    }
    for (hintInfo in previewEntries.extractedHintInfos) {
      for (diff in hintInfo.activeOptionDiff) {
        when (diff) {
          DeclarativeHintsDumpUtil.ExtractedHintInfo.ActiveOptionDiff.End -> {
            sink.endOption()
          }
          is DeclarativeHintsDumpUtil.ExtractedHintInfo.ActiveOptionDiff.Start -> {
            val opt = sink.options.findOrError(diff.id, providerDescription)
            sink.startOption(opt)
          }
        }
      }
      sink.addPresentation(hintInfo.position, hintInfo.hintFormat, true) {
        text(hintInfo.text)
      }
    }
    val inlayData = sink.finish()
    val preprocessed = DeclarativeInlayHintsPass.preprocessCollectedInlayData(inlayData, editor.document)
    return Runnable {
      DeclarativeInlayHintsPass.applyInlayData(editor, project, preprocessed, sourceId)
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
    isEnabled = isProviderEnabledInSettings()
    customSettingsProvider.persistSettings(project, savedSettings, language)
  }

  @Deprecated("Not used in new UI", ReplaceWith("\"\""))
  override val mainCheckBoxLabel: String
    get() = ""

  override val cases: List<ImmediateConfigurable.Case>
    get() = _cases

  private class PreviewEntries(val caseId: String?, val extractedHintInfos: List<DeclarativeHintsDumpUtil.ExtractedHintInfo>)

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

private class MutableOption(val description: InlayProviderOption, var isEnabled: Boolean)

private class PreviewInlayTreeSink(
  val providerBean: InlayHintsProviderExtensionBean,
  val providerEnabled: Boolean,
  val options: List<MutableOption>,
  val sourceId: String
) {
  private val inlayData = mutableListOf<InlayData>()

  private val activeOptions = ArrayList<MutableOption>()

  fun addPresentation(position: InlayPosition, hintFormat: HintFormat, enabled: Boolean, builder: PresentationTreeBuilder.() -> Unit) {
    val b = PresentationTreeBuilderImpl.createRoot(position)
    b.builder()
    val tree = b.complete()
    inlayData.add(InlayData(
      position = position,
      tooltip = null,
      hintFormat = hintFormat,
      tree = tree,
      providerId = providerBean.requiredProviderId(),
      disabled = !enabled || !providerEnabled || activeOptions.any { !it.isEnabled },
      payloads = null,
      providerClass = providerBean.instance.javaClass,
      sourceId = sourceId
    ))
  }

  fun startOption(option: MutableOption) {
    activeOptions.add(option)
  }

  fun endOption() {
    activeOptions.removeLast()
  }

  fun finish(): List<InlayData> = inlayData
}

private fun List<MutableOption>.findOrError(id: String, providerBean: InlayHintsProviderExtensionBean): MutableOption =
  find { it.description.optionId == id } ?: error("Option $id not found for provider ${providerBean.requiredProviderId()}")