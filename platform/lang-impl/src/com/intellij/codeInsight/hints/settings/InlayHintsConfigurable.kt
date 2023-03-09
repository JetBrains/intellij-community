// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.InlayHintsProviderExtension
import com.intellij.lang.Language
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import java.util.function.Predicate
import javax.swing.JComponent
import javax.swing.JPanel

@Deprecated("Use com.intellij.codeInsight.hints.settings.InlaySettingsConfigurable")
class InlayHintsConfigurable(private val project: Project) : Configurable, Configurable.Composite, Configurable.WithEpDependencies {
  override fun getConfigurables(): Array<Configurable> = emptyArray()

  override fun isModified() = false

  override fun getDisplayName(): String {
    return CodeInsightBundle.message("settings.inlay.hints.panel.name")
  }

  override fun createComponent(): JComponent {
    return JPanel()
  }

  override fun apply() {

  }

  override fun reset() {

  }

  override fun disposeUIResources() {

  }

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> =
    listOf(InlaySettingsProvider.EP.EXTENSION_POINT_NAME, InlayHintsProviderExtension.inlayProviderName) +
    InlaySettingsProvider.EP.getExtensions().flatMap { it.getDependencies() }

  companion object {
    /**
     * Updates settings UI when external change happens (e. g. when some provider is changed).
     */
    @JvmStatic
    @Deprecated("New UI doesn't need it, just drop call")
    fun updateInlayHintsUI() {

    }

    @JvmStatic
    @Deprecated("Use com.intellij.codeInsight.hints.settings.InlaySettingsConfigurableKt.showInlaySettings",
                ReplaceWith("showInlaySettings(project, language, null)"))
    fun showSettingsDialogForLanguage(project: Project, language: Language) {
      showInlaySettings(project, language, null)
    }

    @JvmStatic
    @Deprecated("Use com.intellij.codeInsight.hints.settings.InlaySettingsConfigurableKt.showInlaySettings",
                ReplaceWith("showInlaySettings(project, language, selector)"))
    fun showSettingsDialogForLanguage(project: Project, language: Language, selector: Predicate<InlayProviderSettingsModel>?) {
      if (showInlaySettings(project, language, selector)) {
        return
      }
      throw UnsupportedOperationException()
    }
  }
}
