// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings.language

import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider
import com.intellij.lang.Language
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project

internal class SingleLanguageInlayHintsConfigurable(project: Project, val language: Language) : Configurable, SearchableConfigurable {
  private val panel by lazy {
    SingleLanguageInlayHintsSettingsPanel(getInlayProviderSettingsModels(project, language), language, project)
  }

  companion object {
    fun getInlayProviderSettingsModels(project: Project, language: Language) : Array<InlayProviderSettingsModel> {
      val models = InlaySettingsProvider.EP.getExtensions().flatMap { it.createModels(project, language) }
      if (models.isEmpty()) {
        val provider = InlaySettingsProvider.EP.getExtensions().find { language in it.getSupportedLanguages(project) }!!
        throw IllegalStateException("Inlay settings provider ${provider.javaClass} declared support for language \"${language.id}\" but doesn't provide a model")
      }
      return models.toTypedArray()
    }

    @JvmStatic
    fun getId(language: Language) = "inlay.hints." + language.id

    @JvmStatic
    fun getHelpTopic(language: Language) = "settings.inlayhints.${language.id}"
  }

  override fun isModified() = panel.isModified()

  override fun getDisplayName() = language.displayName

  override fun createComponent() = panel

  override fun apply() {
    panel.apply()
    ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
    InlayHintsPassFactory.forceHintsUpdateOnNextPass()
  }

  override fun getId() = getId(language)

  override fun getHelpTopic() = getHelpTopic(language)

  internal fun getModels() = panel.getModels()

  internal fun setCurrentModel(model: InlayProviderSettingsModel) {
    panel.setCurrentModel(model)
  }

  override fun reset() {
    panel.reset()
  }
}