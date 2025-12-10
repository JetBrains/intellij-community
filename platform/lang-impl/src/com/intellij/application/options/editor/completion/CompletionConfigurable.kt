// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor.completion

import com.intellij.application.options.EditorCompletionConfigurableEP
import com.intellij.application.options.editor.EditorOptionsProvider
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.*
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

private val EP_NAME = ExtensionPointName<EditorCompletionConfigurableEP>("com.intellij.editorCompletionConfigurable")


internal class CompletionConfigurable : BoundCompositeSearchableConfigurable<UnnamedConfigurable>(
  ApplicationBundle.message("title.code.completion"),
  "reference.settingsdialog.IDE.editor.code.completion",
  ID), EditorOptionsProvider, Configurable.WithEpDependencies, SearchableConfigurable.Parent {

  private val allConfigurables: List<UnnamedConfigurable> by lazy { ConfigurableWrapper.createConfigurables(EP_NAME) }

  override fun getId(): String = ID

  override fun getDisplayName(): String = ApplicationBundle.message("title.code.completion")

  override fun getDependencies(): Collection<BaseExtensionPointName<*>> = listOf(EP_NAME)

  override fun createConfigurables(): List<UnnamedConfigurable> {
    return allConfigurables.filterNot { it is Configurable }
  }

  override fun getConfigurables(): Array<Configurable> {
    return allConfigurables.filterIsInstance<Configurable>().toTypedArray()
  }

  // Show parent page content similar to EditorSmartKeysConfigurable so that
  // the parent node appears in the tree and can host child configurables.
  override fun hasOwnContent(): Boolean = true

  override fun createPanel(): DialogPanel = panel {
    row {
      label(CompletionMessageBundle.message("completion.description.label"))
    }
    indent {
      createConfigurableLinkPanel()
    }
    for (configurable in configurables) {
      appendDslConfigurable(configurable)
    }
  }


  private fun Panel.createConfigurableLinkPanel(): Panel = panel {
    allConfigurables.filterIsInstance<BoundConfigurable>().forEach { configurable ->
      row {
         link(configurable.displayName) { e ->
          val component = e.source as? JComponent ?: return@link
          openLink(component, configurable) }
      }
    }
  }

  private fun openLink(mainPanel: JComponent, configurable: Configurable) {
    val dataContext = DataManager.getInstance().getDataContext(mainPanel)
    Settings.KEY.getData(dataContext)?.select(configurable)
  }

  companion object {
    const val ID: String = "editor.preferences.completion"
  }
}
