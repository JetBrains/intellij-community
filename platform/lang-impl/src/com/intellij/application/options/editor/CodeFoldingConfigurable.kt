// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.extensions.BaseExtensionPointName
import com.intellij.openapi.options.BoundCompositeConfigurable
import com.intellij.openapi.options.Configurable.WithEpDependencies
import com.intellij.openapi.options.ConfigurableBuilder
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer

internal class CodeFoldingConfigurable : BoundCompositeConfigurable<CodeFoldingOptionsProvider>(
  ApplicationBundle.message("group.code.folding"), "reference.settingsdialog.IDE.editor.code.folding"),
                                         EditorOptionsProvider, WithEpDependencies {

  companion object {
    const val ID: String = "editor.preferences.folding"
  }

  private lateinit var showGutterOutline: Cell<JBCheckBox>

  override fun createPanel(): DialogPanel {
    val settings = EditorSettingsExternalizable.getInstance()
    val sortedConfigurables = configurables.sortedBy { sortByTitle(it) }

    return panel {
      row {
        val text = if (ExperimentalUI.isNewUI()) ApplicationBundle.message("checkbox.show.code.folding.arrows")
        else ApplicationBundle.message("checkbox.show.code.folding.outline")
        showGutterOutline = checkBox(text)
          .bindSelected(settings::isFoldingOutlineShown, settings::setFoldingOutlineShown)
        if (ExperimentalUI.isNewUI()) {
          comboBox(
            listOf(false, true),
            renderer = textListCellRenderer {
              if (it == true) ApplicationBundle.message("checkbox.show.code.folding.outline.on.hover")
              else ApplicationBundle.message("checkbox.show.code.folding.outline.always")
            }
          ).bindItem({ settings.isFoldingOutlineShownOnlyOnHover }, { settings.isFoldingOutlineShownOnlyOnHover = it!! })
        }
      }

      indent {
        row {
          checkBox(ApplicationBundle.message("checkbox.show.bottom.arrows"))
            .bindSelected(settings::isFoldingEndingsShown, settings::setFoldingEndingsShown)
        }.visible(ExperimentalUI.isNewUI())
          .enabledIf(showGutterOutline.selected)
      }

      row {
        label(ApplicationBundle.message("label.fold.by.default"))
      }.topGap(TopGap.SMALL)

      indent {
        for (configurable in sortedConfigurables) {
          appendDslConfigurable(configurable)
        }
      }
    }
  }

  override fun createConfigurables(): List<CodeFoldingOptionsProvider> {
    return ConfigurableWrapper.createConfigurables(CodeFoldingOptionsProviderEP.EP_NAME)
  }

  override fun getId(): String {
    return ID
  }

  override fun getDependencies(): MutableCollection<BaseExtensionPointName<*>> {
    return mutableSetOf(CodeFoldingOptionsProviderEP.EP_NAME)
  }

  override fun apply() {
    super.apply()
    ApplicationManager.getApplication().invokeLater({ Util.applyCodeFoldingSettingsChanges() }, ModalityState.nonModal())
  }

  private fun sortByTitle(p: CodeFoldingOptionsProvider): String {
    val title = ConfigurableBuilder.getConfigurableTitle(p)
    return if (ApplicationBundle.message("title.general") == title) "" else title ?: "z"
  }

  object Util {

    @JvmStatic
    fun applyCodeFoldingSettingsChanges() {
      reinitAllEditors()
      for (editor in EditorFactory.getInstance().allEditors) {
        val project = editor.project
        if (project != null && !project.isDefault) CodeFoldingManager.getInstance(project).scheduleAsyncFoldingUpdate(editor)
      }
      ApplicationManager.getApplication().messageBus.syncPublisher(EditorOptionsListener.FOLDING_CONFIGURABLE_TOPIC).changesApplied()
    }

  }
}
