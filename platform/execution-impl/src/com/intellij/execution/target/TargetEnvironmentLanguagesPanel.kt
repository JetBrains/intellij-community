// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.layout.*
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Font
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

typealias LanguagesList = ContributedConfigurationsList<LanguageRuntimeConfiguration, LanguageRuntimeType<out LanguageRuntimeConfiguration>>

class TargetEnvironmentLanguagesPanel(private val project: Project,
                                      private val targetEnvironmentType: TargetEnvironmentType<*>,
                                      private val targetSupplier: Supplier<TargetEnvironmentConfiguration>,
                                      val languagesList: LanguagesList,
                                      private val parentRefresh: () -> Unit) {

  private val languagePanels = mutableListOf<LanguagePanel>()

  private lateinit var mainPanel: JPanel

  val component: JComponent by lazy { createComponent() }
  val preferredFocusedComponent: JComponent?
    get() = languagePanels.firstOrNull()?.configurable?.preferredFocusedComponent
  val isModified: Boolean
    get() = languagePanels.any { it.configurable.isModified }

  fun applyAll() = languagePanels.forEach { it.configurable.apply() }

  fun reset() {
    if (isModelListChanged()) {
      recreateRuntimePanels()
      parentRefresh()
    }
    languagePanels.forEach { it.configurable.reset() }
  }

  fun disposeUIResources() {
    languagePanels.forEach { it.configurable.disposeUIResources() }
    languagePanels.clear()
  }

  private fun createComponent(): JComponent {
    val result = BorderLayoutPanel()
    mainPanel = JPanel(VerticalLayout(UIUtil.DEFAULT_VGAP))
    recreateRuntimePanels()

    result.addToCenter(mainPanel)
    result.addToBottom(createAddRuntimeHyperlink())
    return result
  }

  private fun recreateRuntimePanels() {
    languagePanels.clear()
    with(mainPanel) {
      removeAll()
      languagesList.resolvedConfigs().forEach {
        add(createRuntimePanel(it).panel)
      }
    }
  }

  private fun isModelListChanged(): Boolean {
    val modelValues = ArrayList(languagesList.resolvedConfigs())
    val panelValues = ArrayList(languagePanels.map { it.language })

    //System.err.println("Model languages: $modelValues")
    //System.err.println("Panel languages: $panelValues")

    return modelValues != panelValues
  }

  private fun createRuntimePanel(language: LanguageRuntimeConfiguration): LanguagePanel {
    val configurable = language.getRuntimeType().createConfigurable(project, language, targetEnvironmentType, targetSupplier)
    val panel = panel {
      row {
        val separator = TitledSeparator(language.getRuntimeType().configurableDescription)
        separator.titleFont = separator.titleFont.deriveFont(Font.BOLD)
        separator(CCFlags.growX, CCFlags.pushX)
        gearButton(DuplicateRuntimeAction(language), RemoveRuntimeAction(language))
        row {
          val languageUI = configurable.createComponent() ?: throw IllegalStateException("for runtime: $language")
          languageUI(CCFlags.growX)
        }
      }
    }
    configurable.reset()
    return LanguagePanel(language, configurable, panel).also {
      languagePanels.add(it)
    }
  }

  private fun createAddRuntimeHyperlink(): JButton {
    class Item(val type: LanguageRuntimeType<*>?) {
      override fun toString(): String {
        return type?.displayName ?: ExecutionBundle.message("targets.details.hyperlink.add.language.runtime")
      }
    }

    return DropDownLink(Item(null), LanguageRuntimeType.EXTENSION_NAME.extensionList.map { Item(it) }, Consumer {
      val newLanguage = it.type?.createDefaultConfig() ?: return@Consumer
      languagesList.addConfig(newLanguage)
      mainPanel.add(createRuntimePanel(newLanguage).panel)
      parentRefresh()
    })
  }

  private abstract inner class ChangeRuntimeActionBase(protected val language: LanguageRuntimeConfiguration,
                                                       @NlsActions.ActionText text: String) : AnAction(text)

  private inner class DuplicateRuntimeAction(runtime: LanguageRuntimeConfiguration)
    : ChangeRuntimeActionBase(runtime, ExecutionBundle.message("targets.details.action.duplicate.text")) {
    override fun actionPerformed(e: AnActionEvent) {
      val copy = language.getRuntimeType().duplicateConfig(language)
      languagesList.addConfig(copy)
      mainPanel.add(createRuntimePanel(copy).panel)
      parentRefresh()
    }
  }

  private inner class RemoveRuntimeAction(runtime: LanguageRuntimeConfiguration)
    : ChangeRuntimeActionBase(runtime, ExecutionBundle.message("targets.details.action.remove.text")) {
    override fun actionPerformed(e: AnActionEvent) {
      languagesList.removeConfig(language)
      languagePanels.firstOrNull { it.language == language }?.let { removedOne ->
        mainPanel.remove(removedOne.panel)
        removedOne.configurable.disposeUIResources()
      }
      parentRefresh()
    }

    override fun update(e: AnActionEvent) {
      val lastLanguage = languagesList.resolvedConfigs().none { it != language }
      e.presentation.isEnabled = !lastLanguage
    }
  }

  private data class LanguagePanel(val language: LanguageRuntimeConfiguration,
                                   val configurable: Configurable,
                                   val panel: JComponent)
}