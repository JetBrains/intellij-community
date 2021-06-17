// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments

import com.intellij.execution.ui.CommonParameterFragments
import com.intellij.execution.ui.FragmentedSettingsUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.externalSystem.service.ui.completetion.JTextCompletionContributor
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionPopup
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.properties.comap
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@ApiStatus.Experimental
class ExternalSystemTasksAndArgumentsField(
  project: Project,
  tasksAndArgumentsInfo: ExternalSystemTasksAndArgumentsInfo
) : ExtendableTextField() {

  private val propertyGraph = PropertyGraph()
  private val tasksAndArgumentsProperty = propertyGraph.graphProperty { "" }

  var tasksAndArguments by tasksAndArgumentsProperty

  init {
    bind(tasksAndArgumentsProperty.comap { it.trim() })
  }

  init {
    getAccessibleContext().accessibleName = tasksAndArgumentsInfo.emptyState
    emptyText.text = tasksAndArgumentsInfo.emptyState
  }

  init {
    val action = Runnable {
      val dialog = ExternalSystemTasksAndArgumentsDialog(project, tasksAndArgumentsInfo)
      dialog.whenVariantChosen {
        val separator = if (text.endsWith(" ")) "" else " "
        document.insertString(document.length, separator + it.text, null)
      }
      dialog.show()
    }
    val anAction = DumbAwareAction.create { action.run() }
    val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
    anAction.registerCustomShortcutSet(CustomShortcutSet(keyStroke), this, null)
    val keystrokeText = KeymapUtil.getKeystrokeText(keyStroke)
    val tooltip = tasksAndArgumentsInfo.tooltip + " ($keystrokeText)"
    val browseExtension = ExtendableTextComponent.Extension.create(
      AllIcons.General.InlineVariables, AllIcons.General.InlineVariablesHover, tooltip, action)
    addExtension(browseExtension)
  }

  init {
    val textCompletionContributor = JTextCompletionContributor.create {
      tasksAndArgumentsInfo.tablesInfo.flatMap { it.completionInfo }
    }
    TextCompletionPopup(project, this, textCompletionContributor)
  }
}