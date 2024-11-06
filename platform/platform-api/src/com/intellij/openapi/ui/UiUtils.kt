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
import com.intellij.ui.ComponentUtil
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.util.ui.ComponentWithEmptyText
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.event.ActionEvent
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.*
import javax.swing.text.JTextComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath


val PREFERRED_FOCUSED_COMPONENT: Key<JComponent> = Key.create("JComponent.preferredFocusedComponent")

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

inline fun <reified T> JComponent.getOrPutUserData(key: Key<T>, block: () -> T): T {
  return getUserData(key) ?: block().also { putUserData(key, it) }
}

inline fun <reified T> Component.getParentOfType(): T? {
  return ComponentUtil.getParentOfType(T::class.java, this)
}

fun JTextComponent.isTextUnderMouse(e: MouseEvent): Boolean {
  val position = viewToModel2D(e.point)
  return position in 1 until text.length
}

fun Component.isComponentUnderMouse(): Boolean {
  if (mousePosition != null) {
    return true
  }
  val pointerInfo = MouseInfo.getPointerInfo() ?: return false
  val location = pointerInfo.location
  SwingUtilities.convertPointFromScreen(location, this)
  val bounds = Rectangle(0, 0, width, height)
  return bounds.contains(location)
}

fun Component.isFocusAncestor(): Boolean {
  return UIUtil.isFocusAncestor(this)
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

fun <T> ListModel<T>.asSequence(): Sequence<T> = sequence {
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

val TextFieldWithBrowseButton.jbTextField: JBTextField
  get() = textField as JBTextField

val TextFieldWithBrowseButton.emptyText: StatusText
  get() = jbTextField.emptyText

fun <C : TextFieldWithBrowseButton> C.setEmptyState(
  text: @NlsContexts.StatusText String
): C {
  jbTextField.setEmptyState(text)
  return this
}

fun <C> C.setEmptyState(
  text: @NlsContexts.StatusText String
): C where C : Component, C : ComponentWithEmptyText {
  accessibleContext.accessibleName = text
  emptyText.text = text
  return this
}

val <E> ComboBox<E>.collectionModel: CollectionComboBoxModel<E>
  get() = model as CollectionComboBoxModel

fun <T> Iterable<T>.naturalSorted(): List<T> = sortedWith(Comparator.comparing({ it.toString() }, NaturalComparator.INSTANCE))

fun getPresentablePath(path: @NonNls String): @NlsSafe String {
  return FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(path.trim()), false)
}

@JvmOverloads
fun getCanonicalPath(path: @NlsSafe String, removeLastSlash: Boolean = true): @NonNls String {
  return FileUtil.toCanonicalPath(FileUtil.expandUserHome(path.trim()), File.separatorChar, removeLastSlash)
}

/**
 * Injects ellipsis into text if text width is more [maxTextWidth].
 *
 * @param text is text to shorten
 * @param minTextPrefixLength is minimum number of symbol from text which should be present in before ellipsis.
 * @param minTextSuffixLength is minimum number of symbol from text which should be present in after ellipsis.
 * @param maxTextPrefixRatio is maximum ratio between text prefix and suffix.
 * @param maxTextWidth is maximum text width in pixels or other metrics.
 * @param getTextWidth is function which calculates text width in pixels.
 * @param useEllipsisSymbol if false then text will be separated by three dots instead ascii ellipsis symbol.
 *
 * @see StringUtil.shortenTextWithEllipsis
 */
fun shortenTextWithEllipsis(
  text: String,
  minTextPrefixLength: Int = 1,
  minTextSuffixLength: Int = 1,
  maxTextPrefixRatio: Float = 0.3f,
  maxTextWidth: Int,
  getTextWidth: (String) -> Int,
  useEllipsisSymbol: Boolean = false
): @NlsSafe String = shortenText(
  text = text,
  minTextPrefixLength = minTextPrefixLength,
  minTextSuffixLength = minTextSuffixLength,
  maxTextPrefixRatio = maxTextPrefixRatio,
  maxTextWidth = maxTextWidth,
  getTextWidth = getTextWidth,
  symbol = when (useEllipsisSymbol) {
    true -> StringUtil.ELLIPSIS
    else -> StringUtil.THREE_DOTS
  }
)

fun shortenText(
  text: String,
  minTextPrefixLength: Int = 1,
  minTextSuffixLength: Int = 1,
  maxTextPrefixRatio: Float = 0.3f,
  maxTextWidth: Int,
  getTextWidth: (String) -> Int,
  symbol: String
): @NlsSafe String {
  val textWidth = getTextWidth(text)
  if (textWidth <= maxTextWidth) {
    return text
  }
  val minTextLength = symbol.length + minTextPrefixLength + minTextSuffixLength
  val maxTextLength = symbol.length + text.length
  val textLength = binarySearch(minTextLength, maxTextLength) {
    val shortenText = shortenText(text, it, minTextPrefixLength, minTextSuffixLength, maxTextPrefixRatio, symbol)
    getTextWidth(shortenText) <= maxTextWidth
  }
  val shortedText = shortenText(text, textLength, minTextPrefixLength, minTextSuffixLength, maxTextPrefixRatio, symbol)
  if (textWidth <= getTextWidth(shortedText)) {
    return text
  }
  return shortedText
}

private fun shortenText(
  text: String,
  maxTextLength: Int,
  minTextPrefixLength: Int,
  minTextSuffixLength: Int,
  maxTextPrefixRatio: Float,
  symbol: String
): String {
  val textLength = maxOf(0, maxTextLength - symbol.length - minTextPrefixLength - minTextSuffixLength)
  val textPrefixLength = maxOf(minTextPrefixLength, (maxTextPrefixRatio * textLength).toInt())
  val textSuffixLength = maxOf(minTextSuffixLength, textLength - textPrefixLength)
  if (textPrefixLength + textSuffixLength >= text.length) {
    return text
  }
  return text.substring(0, textPrefixLength) + symbol + text.substring(text.length - textSuffixLength)
}

private fun binarySearch(
  startIndex: Int,
  finishIndex: Int,
  condition: (Int) -> Boolean
): Int {
  var leftIndex = startIndex
  var rightIndex = finishIndex
  while (rightIndex - leftIndex > 1) {
    val index = (leftIndex + rightIndex) / 2
    when (condition(index)) {
      true -> leftIndex = index
      else -> rightIndex = index
    }
  }
  return when (condition(rightIndex)) {
    true -> rightIndex
    else -> leftIndex
  }
}
