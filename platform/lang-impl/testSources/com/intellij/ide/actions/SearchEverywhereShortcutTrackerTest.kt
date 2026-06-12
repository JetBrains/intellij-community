// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.ide.actions

import com.intellij.openapi.actionSystem.IdeActions
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
internal class SearchEverywhereShortcutTrackerTest {
  private val doubleShiftShortcut = KeyboardModifierGestureShortcut.newInstance(
    KeyboardGestureAction.ModifierType.dblClick,
    KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK),
  ) as KeyboardModifierGestureShortcut
  private val altDoubleShiftShortcut = KeyboardModifierGestureShortcut.newInstance(
    KeyboardGestureAction.ModifierType.dblClick,
    KeyStroke.getKeyStroke(KeyEvent.VK_SHIFT, InputEvent.SHIFT_MASK or InputEvent.ALT_MASK),
  ) as KeyboardModifierGestureShortcut

  private lateinit var originalShortcuts: List<Shortcut>

  @BeforeEach
  fun setUp() {
    originalShortcuts = activeKeymap().getShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).toList()
    resetShortcuts(listOf(doubleShiftShortcut))
    ModifierKeyDoubleClickHandler.getInstance().unsuppressAction(IdeActions.ACTION_SEARCH_EVERYWHERE)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(IdeActions.ACTION_SEARCH_EVERYWHERE)
  }

  @AfterEach
  fun tearDown() {
    resetShortcuts(originalShortcuts)
    ModifierKeyDoubleClickHandler.getInstance().unsuppressAction(IdeActions.ACTION_SEARCH_EVERYWHERE)
    ModifierKeyDoubleClickHandler.getInstance().unregisterAction(IdeActions.ACTION_SEARCH_EVERYWHERE)
    syncKeymapShortcuts()
  }

  @Test
  fun keymapGestureShortcutRegistersRuntimeDoubleShift() {
    syncKeymapShortcuts()

    assertThat(ModifierKeyDoubleClickHandler.getInstance().isActionRegistered(IdeActions.ACTION_SEARCH_EVERYWHERE)).isTrue()
  }

  @Test
  fun removingKeymapGestureShortcutUnregistersRuntimeDoubleShift() {
    syncKeymapShortcuts()

    resetShortcuts(emptyList())
    syncKeymapShortcuts()

    assertThat(ModifierKeyDoubleClickHandler.getInstance().isActionRegistered(IdeActions.ACTION_SEARCH_EVERYWHERE)).isFalse()
  }

  @Test
  fun getShortcutTextReflectsKeymapGesture() {
    resetShortcuts(listOf(altDoubleShiftShortcut))
    syncKeymapShortcuts()

    assertThat(getShortcutText()).isEqualTo(KeymapUtil.getShortcutText(altDoubleShiftShortcut))
  }

  @Test
  fun getShortcutTextIsNullWithoutKeymapGesture() {
    resetShortcuts(emptyList())
    syncKeymapShortcuts()

    assertThat(getShortcutText()).isNull()
  }

  private fun resetShortcuts(shortcuts: List<Shortcut>) {
    val keymap = activeKeymap()
    runWriteAction {
      keymap.getShortcuts(IdeActions.ACTION_SEARCH_EVERYWHERE).forEach { shortcut ->
        keymap.removeShortcut(IdeActions.ACTION_SEARCH_EVERYWHERE, shortcut)
      }
      shortcuts.forEach { shortcut ->
        keymap.addShortcut(IdeActions.ACTION_SEARCH_EVERYWHERE, shortcut)
      }
    }
  }

  private fun activeKeymap(): Keymap = checkNotNull(KeymapManager.getInstance()).activeKeymap

  private fun syncKeymapShortcuts() {
    val method = ModifierKeyDoubleClickHandler::class.java.getDeclaredMethod("syncKeymapShortcuts")
    method.isAccessible = true
    method.invoke(ModifierKeyDoubleClickHandler.getInstance())
  }

  private fun getShortcutText(): String? {
    val method = SearchEverywhereAction::class.java.getDeclaredMethod("getShortcut")
    method.isAccessible = true
    return method.invoke(null) as String?
  }
}
