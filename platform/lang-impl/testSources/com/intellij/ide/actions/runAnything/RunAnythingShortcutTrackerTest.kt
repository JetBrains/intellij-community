// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.ide.actions.runAnything

import com.intellij.openapi.actionSystem.KeyboardGestureAction
import com.intellij.openapi.actionSystem.KeyboardModifierGestureShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.impl.ModifierKeyDoubleClickHandler
import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@TestApplication
@RunInEdt(writeIntent = true)
internal class RunAnythingShortcutTrackerTest {
  private val doubleCtrlShortcut = KeyboardModifierGestureShortcut.newInstance(
    KeyboardGestureAction.ModifierType.dblClick,
    KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK),
  ) as KeyboardModifierGestureShortcut
  private val shiftDoubleCtrlShortcut = KeyboardModifierGestureShortcut.newInstance(
    KeyboardGestureAction.ModifierType.dblClick,
    KeyStroke.getKeyStroke(KeyEvent.VK_CONTROL, InputEvent.CTRL_MASK or InputEvent.SHIFT_MASK),
  ) as KeyboardModifierGestureShortcut
  private val reservedShortcuts = listOf(doubleCtrlShortcut, shiftDoubleCtrlShortcut)

  private lateinit var originalShortcuts: List<Shortcut>
  private var originalConflictingShortcuts: Map<String, List<Shortcut>> = emptyMap()

  @BeforeEach
  fun setUp() {
    originalShortcuts = activeKeymap().getShortcuts(RunAnythingAction.RUN_ANYTHING_ACTION_ID).toList()
    originalConflictingShortcuts = removeConflictingShortcuts()
    resetRunAnythingShortcuts(listOf(doubleCtrlShortcut))
    ModifierKeyDoubleClickHandler.getInstance().unsuppressAction(RunAnythingAction.RUN_ANYTHING_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(RunAnythingAction.RUN_ANYTHING_ACTION_ID)
  }

  @AfterEach
  fun tearDown() {
    resetRunAnythingShortcuts(originalShortcuts)
    restoreConflictingShortcuts()
    ModifierKeyDoubleClickHandler.getInstance().unsuppressAction(RunAnythingAction.RUN_ANYTHING_ACTION_ID)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(RunAnythingAction.RUN_ANYTHING_ACTION_ID)
    syncKeymapShortcuts()
    originalConflictingShortcuts = emptyMap()
  }

  @Test
  fun keymapGestureShortcutRegistersRuntimeDoubleCtrl() {
    syncKeymapShortcuts()

    assertThat(ModifierKeyDoubleClickHandler.getInstance().isActionRegistered(RunAnythingAction.RUN_ANYTHING_ACTION_ID)).isTrue()
  }

  @Test
  fun removingKeymapGestureShortcutUnregistersRuntimeDoubleCtrl() {
    syncKeymapShortcuts()

    resetRunAnythingShortcuts(emptyList())
    syncKeymapShortcuts()

    assertThat(ModifierKeyDoubleClickHandler.getInstance().isActionRegistered(RunAnythingAction.RUN_ANYTHING_ACTION_ID)).isFalse()
  }

  @Test
  fun runtimeModifierShortcutTextUsesRegisteredKeymapGesture() {
    resetRunAnythingShortcuts(listOf(shiftDoubleCtrlShortcut))
    syncKeymapShortcuts()

    assertThat(RunAnythingAction.computeModifierShortcutText()).isEqualTo(KeymapUtil.getShortcutText(shiftDoubleCtrlShortcut))
  }

  @Test
  fun runtimeModifierShortcutTextIgnoresSuppressedKeymapGesture() {
    syncKeymapShortcuts()

    ModifierKeyDoubleClickHandler.getInstance().suppressShortcut(RunAnythingAction.RUN_ANYTHING_ACTION_ID, doubleCtrlShortcut)

    assertThat(activeKeymap().getShortcuts(RunAnythingAction.RUN_ANYTHING_ACTION_ID)).contains(doubleCtrlShortcut)
    assertThat(RunAnythingAction.computeModifierShortcutText()).isNull()
  }

  private fun resetRunAnythingShortcuts(shortcuts: List<Shortcut>) {
    val keymap = activeKeymap()
    runWriteAction {
      keymap.getShortcuts(RunAnythingAction.RUN_ANYTHING_ACTION_ID).forEach { shortcut ->
        keymap.removeShortcut(RunAnythingAction.RUN_ANYTHING_ACTION_ID, shortcut)
      }
      shortcuts.forEach { shortcut ->
        keymap.addShortcut(RunAnythingAction.RUN_ANYTHING_ACTION_ID, shortcut)
      }
    }
  }

  private fun removeConflictingShortcuts(): Map<String, List<Shortcut>> {
    val keymap = activeKeymap()
    val removedShortcuts = linkedMapOf<String, List<Shortcut>>()
    runWriteAction {
      keymap.actionIdList
        .filter { actionId -> actionId != RunAnythingAction.RUN_ANYTHING_ACTION_ID }
        .forEach { actionId ->
          val conflicts = keymap.getShortcuts(actionId).filter { shortcut -> shortcut.conflictsWithReservedShortcut() }
          if (conflicts.isNotEmpty()) {
            removedShortcuts[actionId] = conflicts
            conflicts.forEach { shortcut -> keymap.removeShortcut(actionId, shortcut) }
          }
        }
    }
    return removedShortcuts
  }

  private fun restoreConflictingShortcuts() {
    val keymap = activeKeymap()
    runWriteAction {
      originalConflictingShortcuts.forEach { (actionId, shortcuts) ->
        shortcuts.forEach { shortcut -> keymap.addShortcut(actionId, shortcut) }
      }
    }
  }

  private fun Shortcut.conflictsWithReservedShortcut(): Boolean {
    return this is KeyboardModifierGestureShortcut && reservedShortcuts.any { shortcut ->
      shortcut.type == type &&
      shortcut.stroke.keyCode == stroke.keyCode &&
      normalizeModifiers(shortcut.stroke.modifiers) == normalizeModifiers(stroke.modifiers)
    }
  }

  private fun normalizeModifiers(modifiers: Int): Int {
    var normalized = modifiers
    if ((modifiers and InputEvent.SHIFT_DOWN_MASK) != 0) normalized = normalized or InputEvent.SHIFT_MASK
    if ((modifiers and InputEvent.ALT_DOWN_MASK) != 0) normalized = normalized or InputEvent.ALT_MASK
    if ((modifiers and InputEvent.CTRL_DOWN_MASK) != 0) normalized = normalized or InputEvent.CTRL_MASK
    if ((modifiers and InputEvent.META_DOWN_MASK) != 0) normalized = normalized or InputEvent.META_MASK
    return normalized
  }

  private fun activeKeymap(): Keymap = checkNotNull(KeymapManager.getInstance()).activeKeymap

  private fun syncKeymapShortcuts() {
    val method = ModifierKeyDoubleClickHandler::class.java.getDeclaredMethod("syncKeymapShortcuts")
    method.isAccessible = true
    method.invoke(ModifierKeyDoubleClickHandler.getInstance())
  }
}
