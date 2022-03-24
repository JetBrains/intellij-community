// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.Language
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import java.util.function.Predicate
import javax.swing.JComponent

const val INLAY_ID = "inlay.hints"

class InlaySettingsConfigurable(val project: Project) : Configurable, SearchableConfigurable, Configurable.NoScroll {

  private val panel: InlaySettingsPanel by lazy  { InlaySettingsPanel(project) }

  override fun createComponent(): JComponent {
    return panel
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return panel.tree
  }

  override fun isModified(): Boolean {
    return panel.isModified()
  }

  override fun apply() {
    panel.apply()
  }

  override fun reset() {
    panel.reset()
  }

  override fun enableSearch(option: String?): Runnable? {
    return panel.enableSearch(option)
  }

  fun selectModel(language: Language, selector: Predicate<InlayProviderSettingsModel>?) {
    panel.selectModel(language, selector)
  }

  override fun getDisplayName(): String = CodeInsightBundle.message("settings.inlay.hints.panel.name")

  override fun getId(): String = INLAY_ID

  override fun getHelpTopic(): String = "settings.inlays"
}

class InlaySettingsConfigurableProvider(val project: Project): ConfigurableProvider() {
  override fun createConfigurable(): Configurable {
    return if (useNewSettings()) InlaySettingsConfigurable(project) else InlayHintsConfigurable(project)
  }
}

private fun useNewSettings() = Registry.`is`("new.inlay.settings", true)

fun showNewSettings(project: Project, language: Language, selector: Predicate<InlayProviderSettingsModel>?): Boolean {
  if (!useNewSettings()) return false
  ShowSettingsUtil.getInstance().showSettingsDialog(project, InlaySettingsConfigurable::class.java) {
    if (selector != null) {
      it.selectModel(language, selector)
    }
  }
  return true
}