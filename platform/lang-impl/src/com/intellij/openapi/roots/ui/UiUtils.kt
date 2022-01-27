// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UiUtils")

package com.intellij.openapi.roots.ui

import com.intellij.openapi.ui.isTextUnderMouse as isTextUnderMouseImpl
import com.intellij.openapi.ui.getActionShortcutText as getActionShortcutTextImpl
import com.intellij.openapi.ui.getKeyStrokes as getKeyStrokesImpl
import com.intellij.openapi.ui.removeKeyboardAction as removeKeyboardActionImpl
import com.intellij.openapi.ui.addKeyboardAction as addKeyboardActionImpl
import com.intellij.openapi.observable.util.whenItemSelected as whenItemSelectedImpl
import com.intellij.openapi.observable.util.whenTextChanged as whenTextModifiedImpl
import com.intellij.openapi.observable.util.whenFocusGained as whenFocusGainedImpl
import com.intellij.openapi.observable.util.onceWhenFocusGained as whenFirstFocusGainedImpl
import com.intellij.openapi.ui.ComboBox
import java.awt.event.*
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.text.JTextComponent

//@formatter:off
@Deprecated("Use function from platform API", ReplaceWith("isTextUnderMouse(e)", "com.intellij.openapi.ui.isTextUnderMouse"))
fun JTextComponent.isTextUnderMouse(e: MouseEvent) = isTextUnderMouseImpl(e)
@Deprecated("Use function from platform API", ReplaceWith("getActionShortcutText(actionId)", "com.intellij.openapi.ui.getActionShortcutText"))
fun getActionShortcutText(actionId: String) = getActionShortcutTextImpl(actionId)
@Deprecated("Use function from platform API", ReplaceWith("getKeyStrokes(*actionIds)", "com.intellij.openapi.ui.getKeyStrokes"))
fun getKeyStrokes(vararg actionIds: String) = getKeyStrokesImpl(*actionIds)
@Deprecated("Use function from platform API", ReplaceWith("removeKeyboardAction(*keyStrokes)", "com.intellij.openapi.ui.removeKeyboardAction"))
fun JComponent.removeKeyboardAction(vararg keyStrokes: KeyStroke) = removeKeyboardActionImpl(*keyStrokes)
@Deprecated("Use function from platform API", ReplaceWith("removeKeyboardAction(keyStrokes)", "com.intellij.openapi.ui.removeKeyboardAction"))
fun JComponent.removeKeyboardAction(keyStrokes: List<KeyStroke>) = removeKeyboardActionImpl(keyStrokes)
@Deprecated("Use function from platform API", ReplaceWith("addKeyboardAction(*keyStrokes) { action(it) }", "com.intellij.openapi.ui.addKeyboardAction"))
fun JComponent.addKeyboardAction(vararg keyStrokes: KeyStroke, action: (ActionEvent) -> Unit) = addKeyboardActionImpl(*keyStrokes) { action(it) }
@Deprecated("Use function from platform API", ReplaceWith("addKeyboardAction(keyStrokes, action)", "com.intellij.openapi.ui.addKeyboardAction"))
fun JComponent.addKeyboardAction(keyStrokes: List<KeyStroke>, action: (ActionEvent) -> Unit) = addKeyboardActionImpl(keyStrokes, action)
@Deprecated("Use function from platform API", ReplaceWith("whenItemSelected(listener)", "com.intellij.openapi.observable.util.whenItemSelected"))
fun <E> ComboBox<E>.whenItemSelected(listener: (E) -> Unit) = whenItemSelectedImpl(listener = listener)
@Deprecated("Use function from platform API", ReplaceWith("whenTextChanged { listener() }", "com.intellij.openapi.observable.util.whenTextChanged"))
fun JTextComponent.whenTextModified(listener: () -> Unit) = whenTextModifiedImpl { listener() }
@Deprecated("Use function from platform API", ReplaceWith("whenFocusGained { listener() }", "com.intellij.openapi.observable.util.whenFocusGained"))
fun JComponent.whenFocusGained(listener: () -> Unit) = whenFocusGainedImpl { listener() }
@Deprecated("Use function from platform API", ReplaceWith("onceWhenFocusGained { listener() }", "com.intellij.openapi.observable.util.onceWhenFocusGained"))
fun JComponent.whenFirstFocusGained(listener: () -> Unit) = whenFirstFocusGainedImpl { listener() }
