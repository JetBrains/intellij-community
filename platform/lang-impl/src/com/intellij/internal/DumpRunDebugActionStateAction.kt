// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.execution.RunManager
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import javax.swing.JComponent

class DumpRunDebugActionStateAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val result = buildString {
      val selectedConfiguration = RunManager.getInstance(project).selectedConfiguration
      appendLine("Selected configuration: ${selectedConfiguration?.name}")
      val toolbar = ActionToolbarImpl.findToolbar(CustomActionsSchema.getInstance().getCorrectedAction("NavBarToolBar") as ActionGroup) ?: run {
        appendLine("No toolbar for action group NavBarToolBar")
        return@buildString
      }
      val presentation = ActionManager.getInstance().getAction("RunConfiguration")?.let { toolbar.getPresentation(it) } ?: run {
        appendLine("No presentation for action RunConfiguration")
        return@buildString
      }
      appendLine("Presentation ID ${System.identityHashCode(presentation)} text: ${presentation.text}")
      val customComponent = presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY)
      if (customComponent == null) {
        appendLine("No custom component attached to presentation")
      }
      else {
        val comboBoxButton = toComboBoxButton(customComponent)
        if (comboBoxButton == null) {
          appendLine("Custom component is $customComponent")
        }
        else {
          appendLine("Custom component ID ${System.identityHashCode(comboBoxButton)} text ${comboBoxButton.text}")
          val comboBoxPresentation = comboBoxButton.presentation
          appendLine("Custom component presentation ID ${System.identityHashCode(comboBoxPresentation)} text ${comboBoxPresentation.text}")
        }
      }
      val comboBoxInToolbar = toolbar.components.mapNotNull { toComboBoxButton(it as JComponent) }.firstOrNull()
      if (comboBoxInToolbar != null) {
        appendLine("Combobox in toolbar ${System.identityHashCode(comboBoxInToolbar)} text ${comboBoxInToolbar.text}")
        val comboBoxPresentation = comboBoxInToolbar.presentation
        appendLine("Combobox in toolbar presentation ID ${System.identityHashCode(comboBoxPresentation)} text ${comboBoxPresentation.text}")
      }
    }
    LOG.info(result)
    Messages.showMessageDialog(project, result, "Run/Debug Configurations", null)
  }

  private fun toComboBoxButton(customComponent: JComponent?) =
    customComponent as? ComboBoxAction.ComboBoxButton ?: customComponent?.components?.singleOrNull() as? ComboBoxAction.ComboBoxButton

  companion object {
    private val LOG = Logger.getInstance(DumpRunDebugActionStateAction::class.java)
  }
}