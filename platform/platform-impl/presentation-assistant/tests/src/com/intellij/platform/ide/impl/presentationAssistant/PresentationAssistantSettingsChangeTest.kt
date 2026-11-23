// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.settings.v2.OS
import com.intellij.platform.settings.v2.PER_OS_SETTINGS
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsValue
import com.intellij.platform.settings.v2.access
import com.intellij.platform.settings.v2.getValue
import com.intellij.platform.settings.v2.setValue
import com.intellij.platform.settings.v2.impl.SettingsKeyRegistry
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Checks that a change of the Presentation Assistant settings lands in the settings v2 keys, and how a hand-written
 * config reads. No project is open, so a turned-on presenter shows no popup.
 */
@TestApplication
class PresentationAssistantSettingsChangeTest {
  @BeforeEach
  @AfterEach
  fun dropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    replacePresentationAssistantConfig()
  }

  @Test
  fun `the presenter follows a value written through the key`(): Unit = timeoutRunBlocking(60.seconds) {
    val presentationAssistant = serviceAsync<PresentationAssistant>()

    PresentationAssistantSettings.SHOW_ACTION_DESCRIPTIONS.setValue(true)
    awaitEquals(true) { withContext(Dispatchers.EDT) { presentationAssistant.hasPresenter } }

    PresentationAssistantSettings.SHOW_ACTION_DESCRIPTIONS.setValue(false)
    awaitEquals(false) { withContext(Dispatchers.EDT) { presentationAssistant.hasPresenter } }
  }

  @Test
  fun `a settings change applies its batch to the keys`(): Unit = timeoutRunBlocking(60.seconds) {
    val change = PresentationAssistantSettings.startChange()
      .setIfChanged(PresentationAssistantSettings.POPUP_SIZE, PresentationAssistantPopupSize.SMALL)
      .setIfChanged(PresentationAssistantSettings.POPUP_ALIGNMENT, PresentationAssistantPopupAlignment.TOP_RIGHT)

    change.apply()

    assertEquals(PresentationAssistantPopupSize.SMALL, PresentationAssistantSettings.POPUP_SIZE.getValue())
    assertEquals(PresentationAssistantPopupAlignment.TOP_RIGHT, PresentationAssistantSettings.popupAlignmentIfNoDelta)
  }

  @Test
  fun `a position from the quick settings drops the offsets of a drag`(): Unit = timeoutRunBlocking(60.seconds) {
    val state = serviceAsync<PresentationAssistant>().configuration
    state.deltaX = 20
    state.deltaY = 30
    assertEquals(null, PresentationAssistantSettings.popupAlignmentIfNoDelta)
    val action = PresentationAssistantQuickSettingsPosition(PresentationAssistantPopupAlignment.TOP_LEFT)

    withContext(Dispatchers.EDT) { action.setSelected(TestActionEvent.createTestEvent(action), true) }

    awaitEquals(PresentationAssistantPopupAlignment.TOP_LEFT) { PresentationAssistantSettings.POPUP_ALIGNMENT.getValue() }
    assertEquals(PresentationAssistantPopupAlignment.TOP_LEFT, PresentationAssistantSettings.popupAlignmentIfNoDelta)
    assertEquals(null, state.deltaX)
    assertEquals(null, state.deltaY)
  }

  @Test
  fun `the toggle action turns the action descriptions on and off`(): Unit = timeoutRunBlocking(60.seconds) {
    val action = TogglePresentationAssistantAction()
    val event = TestActionEvent.createTestEvent(action)

    withContext(Dispatchers.EDT) { action.setSelected(event, true) }
    awaitEquals(true) { PresentationAssistantSettings.SHOW_ACTION_DESCRIPTIONS.getValue() }
    assertEquals(true, action.isSelected(event))

    withContext(Dispatchers.EDT) { action.setSelected(event, false) }
    awaitEquals(false) { PresentationAssistantSettings.SHOW_ACTION_DESCRIPTIONS.getValue() }
    assertEquals(false, action.isSelected(event))
  }

  @Test
  fun `the promoter registers every key`(): Unit = timeoutRunBlocking(60.seconds) {
    val registry = SettingsKeyRegistry.getInstance()
    registry.awaitReady()

    for (key in PresentationAssistantSettings.ALL_KEYS) {
      assertTrue(registry.holds(key), "the registry holds '${key.name}'")
    }
  }

  @Test
  fun `a hand-written enum name reads as its entry`(): Unit = timeoutRunBlocking(60.seconds) {
    replacePresentationAssistantConfig(mapOf(PresentationAssistantSettings.POPUP_ALIGNMENT.identity to SettingsValue.StringValue("BOTTOM_RIGHT")))

    assertEquals(PresentationAssistantPopupAlignment.BOTTOM_RIGHT, PresentationAssistantSettings.POPUP_ALIGNMENT.getValue())
  }

  @Test
  fun `a keymap name reads from the slot of the current OS in the per-OS storage`(): Unit = timeoutRunBlocking(60.seconds) {
    val keymapNameEntry = SettingsValue.MapValue(mapOf(PresentationAssistantSettings.MAIN_KEYMAP_NAME.identity to SettingsValue.StringValue("Emacs")))
    replacePresentationAssistantConfig(mapOf(PER_OS_SETTINGS.identity to SettingsValue.MapValue(mapOf(OS.getValue() to keymapNameEntry))))

    assertEquals("Emacs", PresentationAssistantSettings.MAIN_KEYMAP_NAME.getValue())
  }

  @Test
  fun `a keymap write lands in the slot of the current OS`(): Unit = timeoutRunBlocking(60.seconds) {
    val change = PresentationAssistantSettings.startChange().setIfChanged(PresentationAssistantSettings.ALTERNATIVE_KEYMAP_LABEL, "Written")

    change.apply()

    val appContext = SettingsContext.app()
    val currentOsContext = appContext.access { this[OS] = OS.getValue() }
    val otherOsContext = appContext.access { this[OS] = if (OS.getValue() == "linux") "windows" else "linux" }
    assertEquals("Written", PresentationAssistantSettings.ALTERNATIVE_KEYMAP_LABEL[currentOsContext])
    assertEquals(PresentationAssistantSettings.ALTERNATIVE_KEYMAP_LABEL.default(otherOsContext),
                 PresentationAssistantSettings.ALTERNATIVE_KEYMAP_LABEL[otherOsContext])
  }

  @Test
  fun `an unknown enum name reads as the default`(): Unit = timeoutRunBlocking(60.seconds) {
    replacePresentationAssistantConfig(mapOf(PresentationAssistantSettings.POPUP_SIZE.identity to SettingsValue.StringValue("huge")))

    assertEquals(PresentationAssistantPopupSize.MEDIUM, PresentationAssistantSettings.POPUP_SIZE.getValue())
  }
}
