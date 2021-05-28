// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ExternalSystemUiUtils")

package com.intellij.openapi.externalSystem.service.ui

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionPopup
import com.intellij.openapi.externalSystem.service.ui.completetion.TextCompletionPopup.UpdatePopupType.*
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.DocumentAdapter
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.MouseEvent
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.InputMap
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent
import kotlin.math.abs


fun JTextComponent.isTextUnderMouse(e: MouseEvent): Boolean {
  val position = viewToModel2D(e.point)
  return position in 1 until text.length
}

fun getKeyStrokes(vararg actionIds: String): List<KeyStroke> {
  val keymapManager = KeymapManager.getInstance()
  val activeKeymap = keymapManager.activeKeymap
  return actionIds.asSequence()
    .flatMap { activeKeymap.getShortcuts(it).asSequence() }
    .filterIsInstance<KeyboardShortcut>()
    .flatMap { sequenceOf(it.firstKeyStroke, it.secondKeyStroke) }
    .filterNotNull()
    .toList()
}

fun JComponent.removeKeyStroke(key: KeyStroke) {
  var map: InputMap? = inputMap
  while (map != null) {
    map.remove(key)
    map = map.parent
  }
}

fun JComponent.addKeyboardAction(vararg keyStrokes: KeyStroke, action: (ActionEvent) -> Unit) {
  addKeyboardAction(keyStrokes.toList(), action)
}

fun JComponent.addKeyboardAction(keyStrokes: List<KeyStroke>, action: (ActionEvent) -> Unit) {
  for (keyStroke in keyStrokes) {
    registerKeyboardAction(action, keyStroke, JComponent.WHEN_FOCUSED)
  }
}

fun getUiPath(path: String): String {
  return FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path.trim()), false)
}

fun getModelPath(path: String, removeLastSlash: Boolean = true): String {
  return FileUtil.toCanonicalPath(FileUtil.expandUserHome(path.trim()), File.separatorChar, removeLastSlash)
}

fun JTextComponent.whenTextModified(listener: () -> Unit) {
  document.addDocumentListener(object : DocumentAdapter() {
    override fun textChanged(e: DocumentEvent) {
      listener()
    }
  })
}

fun JComponent.whenFocusGained(listener: () -> Unit) {
  addFocusListener(object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      listener()
    }
  })
}

fun JComponent.whenFirstFocusGained(listener: () -> Unit) {
  addFocusListener(object : FocusAdapter() {
    override fun focusGained(e: FocusEvent) {
      removeFocusListener(this)
      listener()
    }
  })
}

fun JTextComponent.installJTextCompletionPopupTriggers(textCompletionPopup: TextCompletionPopup<JTextComponent>) {
  addCaretListener {
    textCompletionPopup.updatePopup(UPDATE)
  }
  whenFocusGained {
    if (text.isEmpty()) {
      textCompletionPopup.updatePopup(SHOW_IF_HAS_VARIANCES)
    }
  }
  whenFirstFocusGained {
    textCompletionPopup.updatePopup(SHOW_IF_HAS_VARIANCES)
  }
  addKeyboardAction(getKeyStrokes(IdeActions.ACTION_CODE_COMPLETION)) {
    textCompletionPopup.updatePopup(SHOW)
  }
  whenTextModified {
    // Listener for caret update is invoked after all of other modification listeners
    // But we needed crop text completion by updated caret position
    // So invokeLater is here to postpone our completion popup update
    invokeLater {
      textCompletionPopup.updatePopup(SHOW_IF_HAS_VARIANCES)
    }
  }
}
