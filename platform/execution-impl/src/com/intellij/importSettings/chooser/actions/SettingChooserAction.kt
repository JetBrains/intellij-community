// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import javax.swing.JComponent
import javax.swing.JPanel

class SettingChooserAction : DumbAwareAction(), CustomComponentAction {
  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place)
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    super.updateCustomComponent(component, presentation)
  }
}

class SettingChooser {
  private val panel = JPanel()

  fun getComponent(): JComponent {
    return panel
  }
}