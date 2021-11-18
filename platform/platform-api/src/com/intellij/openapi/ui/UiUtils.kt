// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UiUtils")

package com.intellij.openapi.ui

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.DropDownLink
import java.awt.ItemSelectable
import java.awt.event.*
import javax.swing.InputMap
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent


fun JTextComponent.isTextUnderMouse(e: MouseEvent): Boolean {
  val position = viewToModel2D(e.point)
  return position in 1 until text.length
}

fun getActionShortcutText(actionId: String): String {
  val keymapManager = KeymapManager.getInstance()
  val activeKeymap = keymapManager.activeKeymap
  val shortcuts = activeKeymap.getShortcuts(actionId)
  return KeymapUtil.getShortcutsText(shortcuts)
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

fun JComponent.removeKeyboardAction(vararg keyStrokes: KeyStroke) {
  removeKeyboardAction(keyStrokes.toList())
}

fun JComponent.removeKeyboardAction(keyStrokes: List<KeyStroke>) {
  var map: InputMap? = inputMap
  while (map != null) {
    for (keyStroke in keyStrokes) {
      map.remove(keyStroke)
    }
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

fun <E> ComboBox<E>.whenItemSelected(listener: (E) -> Unit) {
  (this as ItemSelectable).whenItemSelected(listener)
}

fun <E> DropDownLink<E>.whenItemSelected(listener: (E) -> Unit) {
  (this as ItemSelectable).whenItemSelected(listener)
}

fun <T> ItemSelectable.whenItemSelected(listener: (T) -> Unit) {
  addItemListener { event ->
    if (event.stateChange == ItemEvent.SELECTED) {
      @Suppress("UNCHECKED_CAST")
      listener(event.item as T)
    }
  }
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

fun JComponent.whenMousePressed(listener: (MouseEvent) -> Unit) {
  addMouseListener(object : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
      listener(e)
    }
  })
}
