// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.Language
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Unmodifiable
import java.util.function.Predicate
import javax.swing.JComponent

const val INLAY_ID: String = "inlay.hints"

class InlaySettingsConfigurable(val project: Project) : Configurable, SearchableConfigurable, Configurable.NoScroll, Configurable.VariableProjectAppLevel, InlaySettingsConfigurableApi {

  private val panel: InlaySettingsPanel by lazy  { InlaySettingsPanel(project) }

  override fun createComponent(): JComponent {
    return panel.content
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

  override fun selectModel(language: Language, selector: Predicate<InlayProviderSettingsModel>?) {
    panel.selectModel(language, selector)
  }

  override fun getDisplayName(): String = CodeInsightBundle.message("settings.inlay.hints.panel.name")

  override fun getId(): String = INLAY_ID

  override fun getHelpTopic(): String = "settings.inlays"

  override fun isProjectLevel(): Boolean = false
}

@ApiStatus.Internal
interface InlaySettingsConfigurableApi : Configurable {
  fun selectModel(language: Language, selector: Predicate<InlayProviderSettingsModel>?)
}

@ApiStatus.Internal
class InlaySettingsConfigurableProvider(val project: Project): ConfigurableProvider(), Configurable.WithEpDependencies {
  override fun createConfigurable(): Configurable {
    return InlaySettingsConfigurableFactory.EP_NAME.extensionList.firstOrNull()?.create(project) ?: InlaySettingsConfigurable(project)
  }

  override fun getConfigurableType(): Class<*> {
    return InlaySettingsConfigurableApi::class.java
  }

  override fun getDependencies(): @Unmodifiable Collection<BaseExtensionPointName<*>?> =
    listOf(InlaySettingsConfigurableFactory.EP_NAME)
}

fun showInlaySettings(project: Project, language: Language, selector: Predicate<InlayProviderSettingsModel>?): Boolean {
  ShowSettingsUtil.getInstance().showSettingsDialog(project, InlaySettingsConfigurableApi::class.java) {
    if (selector != null) {
      it.selectModel(language, selector)
    }
  }
  return true
}