// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UiUtils")

package com.intellij.openapi.ui

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.ComponentWithEmptyText
import java.awt.Component
import java.awt.event.*
import java.io.File
import javax.swing.*
import javax.swing.text.JTextComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

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

fun ExtendableTextField.addExtension(
  icon: Icon,
  hoverIcon: Icon = icon,
  tooltip: @NlsContexts.Tooltip String? = null,
  action: () -> Unit
) {
  addExtension(ExtendableTextComponent.Extension.create(icon, hoverIcon, tooltip, action))
}

fun <T> ListModel<T>.asSequence() = sequence<T> {
  for (i in 0 until size) {
    yield(getElementAt(i))
  }
}

fun TreeModel.asSequence(): Sequence<DefaultMutableTreeNode> {
  val root = root ?: return emptySequence()
  return (root as DefaultMutableTreeNode)
    .depthFirstEnumeration()
    .asSequence()
    .map { it as DefaultMutableTreeNode }
}

fun TreeModel.getTreePath(userObject: Any?): TreePath? =
  asSequence()
    .filter { it.userObject == userObject }
    .firstOrNull()
    ?.let { TreePath(it.path) }

val TextFieldWithBrowseButton.emptyText
  get() = (textField as JBTextField).emptyText

fun <C> C.setEmptyState(
  text: @NlsContexts.StatusText String
): C where C : Component, C : ComponentWithEmptyText = apply {
  getAccessibleContext().accessibleName = text
  emptyText.text = text
}

val <E> ComboBox<E>.collectionModel: CollectionComboBoxModel<E>
  get() = model as CollectionComboBoxModel

fun <T> Iterable<T>.naturalSorted() = sortedWith(Comparator.comparing({ it.toString() }, NaturalComparator.INSTANCE))

fun getPresentablePath(path: String): @NlsSafe String {
  return FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path.trim()), false)
}

fun getCanonicalPath(path: String, removeLastSlash: Boolean = true): String {
  return FileUtil.toCanonicalPath(FileUtil.expandUserHome(path.trim()), File.separatorChar, removeLastSlash)
}
