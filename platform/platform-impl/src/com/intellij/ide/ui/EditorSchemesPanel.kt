// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.application.options.colors.ColorSchemeActions
import com.intellij.application.options.colors.SchemesPanel
import com.intellij.application.options.schemes.AbstractSchemeActions
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.ActionLink
import java.awt.Component

internal class EditorSchemesPanel(val colorAndFontsOptions: ColorAndFontOptions): SchemesPanel(colorAndFontsOptions, 0) {
  init {
    setSeparatorVisible(false)
  }

  override fun getComboBoxLabel(): String {
    return IdeBundle.message("combobox.editor.color.scheme")
  }

  override fun createSchemeActions(): AbstractSchemeActions<EditorColorsScheme> {
    val component = this

    return object: ColorSchemeActions(this) {
      override fun getActions(): MutableCollection<AnAction> {
        val list = mutableListOf<AnAction>()

        list.add(OpenEditorSchemeConfigurableAction(component))
        list.add(Separator())
        list.addAll(getExportImportActions(false))
        return list
      }

      override fun onSchemeChanged(scheme: EditorColorsScheme?) {
        onSchemeChangedFromAction(scheme)
      }

      override fun renameScheme(scheme: EditorColorsScheme, newName: String) {
        renameSchemeFromAction(scheme, newName)
      }

      override fun getOptions(): ColorAndFontOptions = colorAndFontsOptions
    }
  }

  override fun createActionLink(): ActionLink? = null

  override fun getContextHelpLabelText(): String? = null
}

private class OpenEditorSchemeConfigurableAction(val component: Component) :
  DumbAwareAction(IdeBundle.message("combobox.editor.color.scheme.edit")) {

  override fun actionPerformed(e: AnActionEvent) {
    val settings = Settings.KEY.getData(DataManager.getInstance().getDataContext(component))
    settings?.select(settings.find("reference.settingsdialog.IDE.editor.colors"))
  }
}