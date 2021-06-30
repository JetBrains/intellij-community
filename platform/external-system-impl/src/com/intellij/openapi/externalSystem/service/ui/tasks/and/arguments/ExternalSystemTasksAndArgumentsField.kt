// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.tasks.and.arguments

import com.intellij.execution.ui.CommonParameterFragments
import com.intellij.execution.ui.FragmentedSettingsUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.ui.completetion.JTextCompletionContributor
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionContributor.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionPopup
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
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
  externalSystemId: ProjectSystemId,
  tasksAndArguments: ExternalSystemTasksAndArguments
) : ExtendableTextField() {

  private val propertyGraph = PropertyGraph()
  private val tasksAndArgumentsProperty = propertyGraph.graphProperty { "" }

  var tasksAndArguments by tasksAndArgumentsProperty

  init {
    bind(tasksAndArgumentsProperty.comap { it.trim() })
  }

  init {
    val message = ExternalSystemBundle.message("run.configuration.tasks.and.arguments.empty.state")
    getAccessibleContext().accessibleName = message
    emptyText.text = message
    FragmentedSettingsUtil.setupPlaceholderVisibility(this)
    CommonParameterFragments.setMonospaced(this)
  }

  init {
    val action = Runnable {
      val dialog = ExternalSystemTasksAndArgumentsDialog(project, externalSystemId, tasksAndArguments)
      dialog.whenItemChosen {
        val separator = if (text.endsWith(" ")) "" else " "
        document.insertString(document.length, separator + it.name, null)
      }
      dialog.show()
    }
    val anAction = DumbAwareAction.create { action.run() }
    val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
    anAction.registerCustomShortcutSet(CustomShortcutSet(keyStroke), this, null)
    val keystrokeText = KeymapUtil.getKeystrokeText(keyStroke)
    val tooltip = ExternalSystemBundle.message("run.configuration.tasks.and.arguments.tooltip") + " ($keystrokeText)"
    val browseExtension = ExtendableTextComponent.Extension.create(
      AllIcons.General.InlineVariables, AllIcons.General.InlineVariablesHover, tooltip, action)
    addExtension(browseExtension)
  }

  init {
    val textCompletionContributor = JTextCompletionContributor.create {
      tasksAndArguments.tasks.map { TextCompletionInfo(it.name, it.description) } +
      tasksAndArguments.arguments.mapNotNull { it.shortName?.let { n -> TextCompletionInfo(n, it.description) } } +
      tasksAndArguments.arguments.map { TextCompletionInfo(it.name, it.description) }
    }
    TextCompletionPopup(project, this, textCompletionContributor)
  }
}