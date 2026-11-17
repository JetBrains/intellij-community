// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.settings.v2.OS
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.access
import com.intellij.platform.settings.v2.getValue
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Checks that [PresentationAssistantSettingsImport] imports the settings of the old XML file into the settings v2
 * keys. A test gives the state to the component, as the store does when it loads the file, and runs the
 * migration.
 */
@TestApplication
class PresentationAssistantSettingsImportTest {
  @BeforeEach
  @AfterEach
  fun dropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    replacePresentationAssistantConfig()
  }

  @Test
  fun `the migration imports each value of the old settings into its key`(): Unit = timeoutRunBlocking(60.seconds) {
    val legacyState = PresentationAssistantState().apply {
      showActionDescriptions = true
      popupSize = PresentationAssistantPopupSize.LARGE.value
      popupDuration = 7000
      horizontalAlignment = PresentationAssistantPopupAlignment.TOP_LEFT.x
      verticalAlignment = PresentationAssistantPopupAlignment.TOP_LEFT.y
      deltaX = 10
      deltaY = -5
      theme = PresentationAssistantTheme.PALE.value
      mainKeymapName = "Emacs"
      mainKeymapLabel = "Main label"
      showAlternativeKeymap = true
      alternativeKeymapName = "Eclipse"
      alternativeKeymapLabel = "Alternative label"
    }

    migrate(legacyState)

    assertEquals(true, PresentationAssistantSettings.SHOW_ACTION_DESCRIPTIONS.getValue())
    assertEquals(PresentationAssistantPopupSize.LARGE, PresentationAssistantSettings.POPUP_SIZE.getValue())
    assertEquals(7000, PresentationAssistantSettings.POPUP_DURATION_MILLIS.getValue())
    assertEquals(PresentationAssistantPopupAlignment.TOP_LEFT, PresentationAssistantSettings.POPUP_ALIGNMENT.getValue())
    assertEquals(10, serviceAsync<PresentationAssistant>().configuration.deltaX, "the offsets stay in the state")
    assertEquals(-5, serviceAsync<PresentationAssistant>().configuration.deltaY, "the offsets stay in the state")
    assertEquals(PresentationAssistantTheme.PALE, PresentationAssistantSettings.THEME.getValue())
    assertEquals("Emacs", PresentationAssistantSettings.MAIN_KEYMAP_NAME.getValue())
    assertEquals("Main label", PresentationAssistantSettings.MAIN_KEYMAP_LABEL.getValue())
    assertEquals(true, PresentationAssistantSettings.SHOW_ALTERNATIVE_KEYMAP.getValue())
    assertEquals("Eclipse", PresentationAssistantSettings.ALTERNATIVE_KEYMAP_NAME.getValue())
    assertEquals("Alternative label", PresentationAssistantSettings.ALTERNATIVE_KEYMAP_LABEL.getValue())

    val appContext = SettingsContext.app()
    val otherOsContext = appContext.access { this[OS] = if (OS.getValue() == "linux") "windows" else "linux" }
    for (key in listOf(PresentationAssistantSettings.MAIN_KEYMAP_NAME, PresentationAssistantSettings.MAIN_KEYMAP_LABEL,
                       PresentationAssistantSettings.ALTERNATIVE_KEYMAP_NAME, PresentationAssistantSettings.ALTERNATIVE_KEYMAP_LABEL)) {
      assertEquals(key.default(otherOsContext), key[otherOsContext], "the value of '${key.name}' in the slot of another OS")
    }
  }

  @Test
  fun `the migration of the default old settings stores no value`(): Unit = timeoutRunBlocking(60.seconds) {
    migrate(PresentationAssistantState())

    val appContext = SettingsContext.app()
    for (key in PresentationAssistantSettings.ALL_KEYS) {
      assertEquals(emptyList<Any?>(), key.values(appContext), "the stored values of '${key.name}'")
      assertEquals(key.default(appContext), key[appContext], "the value of '${key.name}'")
    }
  }

  @Test
  fun `a repeated migration changes nothing`(): Unit = timeoutRunBlocking(60.seconds) {
    val legacyState = PresentationAssistantState().apply { popupDuration = 9000 }
    migrate(legacyState)
    val appContext = SettingsContext.app()
    val storedAfterFirstRun = PresentationAssistantSettings.ALL_KEYS.associateWith { it.values(appContext) }

    migrate(legacyState)

    assertEquals(storedAfterFirstRun, PresentationAssistantSettings.ALL_KEYS.associateWith { it.values(appContext) })
    assertEquals(9000, PresentationAssistantSettings.POPUP_DURATION_MILLIS.getValue())
  }

  @Test
  fun `a repeated migration keeps a value that the user changed after the import`(): Unit = timeoutRunBlocking(60.seconds) {
    val legacyState = PresentationAssistantState().apply { popupDuration = 9000 }
    migrate(legacyState)
    PresentationAssistantSettings.startChange().setIfChanged(PresentationAssistantSettings.POPUP_DURATION_MILLIS, 5000).apply()

    migrate(legacyState)

    assertEquals(5000, PresentationAssistantSettings.POPUP_DURATION_MILLIS.getValue())
  }

  /** Gives [legacyState] to the component and runs the migration, as the platform does after the first start. */
  private suspend fun migrate(legacyState: PresentationAssistantState) {
    serviceAsync<PresentationAssistant>().loadState(legacyState)
    PresentationAssistantSettingsImport().migrate(SettingsContext.app())
  }
}
