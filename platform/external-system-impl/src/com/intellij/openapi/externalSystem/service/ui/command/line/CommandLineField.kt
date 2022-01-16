// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.command.line

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.externalSystem.service.ui.completion.JTextCompletionContributor
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionPopup
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.observable.properties.AtomicLazyProperty
import com.intellij.openapi.observable.properties.comap
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.layout.*
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class CommandLineField(
  project: Project,
  commandLineInfo: CommandLineInfo
) : ExtendableTextField() {

  private val commandLineProperty = AtomicLazyProperty { "" }

  var commandLine by commandLineProperty

  init {
    bind(commandLineProperty.comap { it.trim() })
  }

  init {
    getAccessibleContext().accessibleName = commandLineInfo.fieldEmptyState
    emptyText.text = commandLineInfo.fieldEmptyState
  }

  init {
    val textCompletionContributor = JTextCompletionContributor.create<CommandLineField> {
      commandLineInfo.tablesInfo.flatMap { it.completionInfo }
    }
    val textCompletionPopup = TextCompletionPopup(project, this, textCompletionContributor)
    val action = Runnable {
      textCompletionPopup.updatePopup(TextCompletionPopup.UpdatePopupType.HIDE)
      val dialog = CommandLineDialog(project, commandLineInfo)
      dialog.whenVariantChosen {
        val separator = if (text.endsWith(" ") || text.isEmpty()) "" else " "
        document.insertString(document.length, separator + it.text, null)
      }
      dialog.show()
    }
    val anAction = DumbAwareAction.create { action.run() }
    val keyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
    anAction.registerCustomShortcutSet(CustomShortcutSet(keyStroke), this, null)
    val keystrokeText = KeymapUtil.getKeystrokeText(keyStroke)
    val tooltip = commandLineInfo.dialogTooltip + " ($keystrokeText)"
    val browseExtension = ExtendableTextComponent.Extension.create(
      AllIcons.General.InlineVariables, AllIcons.General.InlineVariablesHover, tooltip, action)
    addExtension(browseExtension)
  }
}