// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings.language

import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.configurationStore.deserializeInto
import com.intellij.configurationStore.serialize
import com.intellij.lang.Language
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.ResourceUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.xmlb.SerializationFilter

class NewInlayProviderSettingsModel<T : Any>(
  private val providerWithSettings: ProviderWithSettings<T>,
  private val config: InlayHintsSettings
) : InlayProviderSettingsModel(
  isEnabled = config.hintsEnabled(providerWithSettings.provider.key, providerWithSettings.language),
  id = providerWithSettings.provider.key.id,
  language = providerWithSettings.language
) {
  override val name: String
    get() = providerWithSettings.provider.name
  override val mainCheckBoxLabel: String
    get() = providerWithSettings.configurable.mainCheckboxText
  override val group: InlayGroup
    get() = providerWithSettings.provider.group

  override fun createFile(project: Project, fileType: FileType, document: Document): PsiFile =
    providerWithSettings.provider.createFile(project, fileType, document)

  override val description: String?
    get() = providerWithSettings.provider.description

  override val component by lazy {
    providerWithSettings.configurable.createComponent(onChangeListener!!)
  }

  override fun collectAndApply(editor: Editor, file: PsiFile) {
    providerWithSettings.provider.preparePreview(editor, file)
    providerWithSettings.getCollectorWrapperFor(file, editor, providerWithSettings.language)?.let { collectorWrapperFor ->
      ReadAction.nonBlocking {
        collectorWrapperFor.collectTraversingAndApplyOnEdt(editor, file, isEnabled)
      }.inSmartMode(file.project)
        .submit(AppExecutorUtil.getAppExecutorService())
    }
  }

  override val cases: List<ImmediateConfigurable.Case>
    get() = providerWithSettings.configurable.cases

  override val previewText: String?
    get() = providerWithSettings.provider.previewText

  override fun getCasePreview(case: ImmediateConfigurable.Case?): String? {
    return getCasePreview(providerWithSettings.language, providerWithSettings.provider, case)
  }

  override fun getCaseDescription(case: ImmediateConfigurable.Case): String? {
    val key = "inlay." + providerWithSettings.provider.key.id + "." + case.id
    return providerWithSettings.provider.getProperty(key)
  }

  override fun apply() {
    val copy = providerWithSettings.withSettingsCopy()
    config.storeSettings(copy.provider.key, copy.language, copy.settings)
    config.changeHintTypeStatus(copy.provider.key, copy.language, isEnabled)
  }

  override fun isModified(): Boolean {
    if (isEnabled != config.hintsEnabled(providerWithSettings.provider.key, providerWithSettings.language)) return true
    val inSettings = providerWithSettings.settings
    val stored = providerWithSettings.provider.getActualSettings(config, providerWithSettings.language)
    return inSettings != stored
  }

  override fun toString(): String = language.displayName + ": " + name

  override fun reset() {
    // Workaround for deep copy
    val obj = providerWithSettings.provider.getActualSettings(config, providerWithSettings.language)
    val element = serialize(obj, SerializationFilter { _, _ -> true })
    element?.deserializeInto(providerWithSettings.settings)
    providerWithSettings.configurable.reset()
    isEnabled = config.hintsEnabled(providerWithSettings.provider.key, providerWithSettings.language)
  }
}

fun getCasePreview(language: Language, provider: Any, case: ImmediateConfigurable.Case?): String? {
  val key = (provider as? InlayHintsProvider<*>)?.key?.id ?: "Parameters"
  val fileType = language.associatedFileType ?: PlainTextFileType.INSTANCE
  val path = "inlayProviders/" + key + "/" + (case?.id ?: "preview") + "." + fileType.defaultExtension
  val stream = provider.javaClass.classLoader.getResourceAsStream(path)
  return if (stream != null) ResourceUtil.loadText(stream) else null
}