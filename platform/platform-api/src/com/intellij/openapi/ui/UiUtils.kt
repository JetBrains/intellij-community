// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UiUtils")
@file:Suppress("SameParameterValue")

package com.intellij.openapi.ui

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.ComponentWithEmptyText
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.event.*
import java.io.File
import javax.swing.*
import javax.swing.text.JTextComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

val PREFERRED_FOCUSED_COMPONENT = Key.create<JComponent>("JComponent.preferredFocusedComponent")

fun JComponent.getPreferredFocusedComponent(): JComponent? {
  if (this is DialogPanel) {
    return preferredFocusedComponent
  }
  return getUserData(PREFERRED_FOCUSED_COMPONENT)
}

fun JComponent.addPreferredFocusedComponent(component: JComponent) {
  putUserData(PREFERRED_FOCUSED_COMPONENT, component)
}

fun <T> JComponent.putUserData(key: Key<T>, data: T?) {
  putClientProperty(key, data)
}

inline fun <reified T> JComponent.getUserData(key: Key<T>): T? {
  return getClientProperty(key) as? T
}

fun JTextComponent.isTextUnderMouse(e: MouseEvent): Boolean {
  val position = viewToModel2D(e.point)
  return position in 1 until text.length
}

fun Component.isComponentUnderMouse(): Boolean {
  val pointerInfo = MouseInfo.getPointerInfo() ?: return false
  val location = pointerInfo.location
  SwingUtilities.convertPointFromScreen(location, this)
  val bounds = Rectangle(0, 0, width, height)
  return bounds.contains(location)
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

fun getPresentablePath(path: @NonNls String): @NlsSafe String {
  return FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path.trim()), false)
}

@JvmOverloads
fun getCanonicalPath(path: @NonNls String, removeLastSlash: Boolean = true): @NonNls String {
  return FileUtil.toCanonicalPath(FileUtil.expandUserHome(path.trim()), File.separatorChar, removeLastSlash)
}

fun JComponent.getTextWidth(text: @NlsSafe String): Int {
  return getFontMetrics(font).stringWidth(text)
}

fun shortenTextWithEllipsis(
  text: String,
  maxWidth: Int,
  getTextWidth: (String) -> Int,
  getFullText: (String) -> String = { it },
  useEllipsisSymbol: Boolean = false
): String {
  val symbol = when (useEllipsisSymbol) {
    true -> StringUtil.ELLIPSIS
    else -> StringUtil.THREE_DOTS
  }
  return shortenText(
    text = text,
    maxWidth = maxWidth,
    getFullText = getFullText,
    getTextWidth = getTextWidth,
    getShortenText = { it, length ->
      val maxLength = maxOf(length, symbol.length + 2)
      val suffixLength = maxOf(1, (0.7 * (maxLength - symbol.length)).toInt())
      StringUtil.shortenTextWithEllipsis(it, maxLength, suffixLength, symbol)
    }
  )
}

private fun shortenText(
  text: String,
  maxWidth: Int,
  getFullText: (String) -> String,
  getTextWidth: (String) -> Int,
  getShortenText: (String, Int) -> String
): @NlsSafe String {
  val length = binarySearch(0, text.length) {
    val shortenText = getShortenText(text, it)
    getTextWidth(getFullText(shortenText)) <= maxWidth
  }
  return getFullText(getShortenText(text, length ?: 0))
}

private fun binarySearch(
  startIndex: Int,
  finishIndex: Int,
  isOk: (Int) -> Boolean
): Int? {
  if (!isOk(startIndex)) {
    return null
  }
  if (isOk(finishIndex)) {
    return finishIndex
  }

  var leftIndex = startIndex
  var rightIndex = finishIndex
  while (leftIndex + 1 < rightIndex) {
    val index = (leftIndex + rightIndex) / 2
    if (isOk(index)) {
      leftIndex = index
    }
    else {
      rightIndex = index
    }
  }
  return leftIndex
}
