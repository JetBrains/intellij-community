// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.panel
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

class TargetCustomToolPanel(private val project: Project,
                            private val targetEnvironmentType: TargetEnvironmentType<*>,
                            private val targetSupplier: Supplier<TargetEnvironmentConfiguration>,
                            private val language: LanguageRuntimeConfiguration,
                            private val introspectable: LanguageRuntimeType.Introspectable?) {

  val component: JComponent by lazy { createComponent() }

  private lateinit var mainPanel: JPanel

  private var languagePanel: LanguagePanel? = null

  val preferredFocusedComponent: JComponent?
    get() = null

  private fun createComponent(): JComponent {
    val result = BorderLayoutPanel()
    mainPanel = JPanel(VerticalLayout(UIUtil.DEFAULT_VGAP))
    recreateRuntimePanels()

    result.addToCenter(mainPanel)
    return result
  }

  fun applyAll() {
    languagePanel?.configurable?.apply()
  }

  /**
   * While [targetSupplier] might return temp [TargetEnvironmentConfiguration], [preparedConfiguration] is the actual configuration that can be
   * used for creating Python SDK.
   * Call [validateCustomTool] before this method to make sure there are no errors
   */
  fun createCustomTool(preparedConfiguration: TargetEnvironmentConfiguration): Any? {
    return customToolLanguageConfigurable?.createCustomTool(preparedConfiguration)
  }

  fun validateCustomTool(): Collection<ValidationInfo> = customToolLanguageConfigurable?.validate() ?: emptyList()

  private val customToolLanguageConfigurable: CustomToolLanguageConfigurable<*>?
    get() =
      (languagePanel?.configurable as? CustomToolLanguageConfigurable<*>)

  fun disposeUIResources() = Unit

  private fun recreateRuntimePanels() {
    languagePanel = null
    with(mainPanel) {
      removeAll()
      add(createRuntimePanel(language).panel)
    }
  }

  private fun createRuntimePanel(language: LanguageRuntimeConfiguration): LanguagePanel {
    val configurable = language.getRuntimeType().createConfigurable(project, language, targetEnvironmentType, targetSupplier)
    if (introspectable != null) {
      (configurable as? CustomToolLanguageConfigurable<*>)?.setIntrospectable(introspectable)
    }
    val panel = panel {
      row {
        val languageUI = configurable.createComponent() ?: throw IllegalStateException("for runtime: $language")
        languageUI(CCFlags.growX)
      }
    }
    configurable.reset()
    return LanguagePanel(language, configurable, panel).also {
      languagePanel = it
    }
  }

  private data class LanguagePanel(val language: LanguageRuntimeConfiguration,
                                   val configurable: Configurable,
                                   val panel: JComponent)
}