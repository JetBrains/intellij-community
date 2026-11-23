// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.ui.generate

import com.intellij.java.JavaBundle
import com.intellij.lang.Language
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.options.TabbedConfigurable
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.java.generate.GenerateToStringConfigurable
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManagerProvider
import org.jetbrains.java.generate.view.TemplatesPanel
import java.awt.Component

@ApiStatus.Internal
object GenerateToStringSettingsDialog {
  /**
   * Shows the modal dialog with the toString generation settings and the toString templates of [language].
   * The method returns after the dialog closes.
   * The method does nothing if no [ToStringTemplatesManagerProvider] is registered for [language].
   */
  @JvmStatic
  fun show(parent: Component?, project: Project, language: Language) {
    val templatesManager = ToStringTemplatesManagerProvider.forLanguage(language) ?: return
    val templatesPanel = TemplatesPanel(project, templatesManager)
    val composite = object : TabbedConfigurable() {
      override fun createConfigurables(): List<Configurable> = listOf(GenerateToStringConfigurable(project), templatesPanel)

      override fun getDisplayName(): String = JavaBundle.message("generate.tostring.tab.title")

      override fun getHelpTopic(): String = "editing.altInsert.tostring.settings"
    }
    try {
      ShowSettingsUtil.getInstance().editConfigurable(parent, composite) {
        templatesPanel.selectItem(templatesManager.defaultTemplate)
      }
    }
    finally {
      composite.disposeUIResources()
    }
  }
}
