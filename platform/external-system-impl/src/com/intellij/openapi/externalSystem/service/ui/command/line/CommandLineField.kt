// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.command.line

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionField
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfo
import com.intellij.openapi.externalSystem.service.ui.completion.TextCompletionInfoRenderer
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.observable.util.trim
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.setEmptyState
import com.intellij.ui.components.fields.ExtendableTextComponent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class CommandLineField(
  project: Project,
  private val commandLineInfo: CommandLineInfo
) : TextCompletionField<TextCompletionInfo>(project) {

  private val commandLineProperty = AtomicProperty("")

  var commandLine by commandLineProperty

  override fun getCompletionVariants(): List<TextCompletionInfo> {
    return commandLineInfo.tablesInfo.flatMap { it.completionInfo }
  }

  init {
    bind(commandLineProperty.trim())
  }

  init {
    renderer = TextCompletionInfoRenderer()
    completionType = CompletionType.REPLACE_WORD
    setEmptyState(commandLineInfo.fieldEmptyState)
  }

  init {
    val action = Runnable {
      updatePopup(UpdatePopupType.HIDE)
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