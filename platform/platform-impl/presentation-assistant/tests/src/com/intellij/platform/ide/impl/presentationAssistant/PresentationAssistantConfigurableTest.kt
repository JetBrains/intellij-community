// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.presentationAssistant

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.ui.ComboBox
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.getValue
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the settings page of the Presentation Assistant against the settings v2 keys. A test edits the inputs as a
 * user does, through the combo boxes of the page.
 */
@TestApplication
class PresentationAssistantConfigurableTest {
  private val configurable = PresentationAssistantConfigurable()

  @BeforeEach
  fun dropStoredSettingsBefore(): Unit = timeoutRunBlocking(60.seconds) {
    replacePresentationAssistantConfig()
  }

  @AfterEach
  fun disposePageAndDropStoredSettings(): Unit = timeoutRunBlocking(60.seconds) {
    withContext(Dispatchers.EDT) { configurable.disposeUIResources() }
    replacePresentationAssistantConfig()
  }

  @Test
  fun `an Apply writes the changed input to its key`(): Unit = timeoutRunBlocking(60.seconds) {
    val sizeInput = withContext(Dispatchers.EDT) {
      val page = configurable.createComponent()
      assertFalse(configurable.isModified, "a new page is not modified")
      page.comboBoxOf<PresentationAssistantPopupSize>().also { it.selectedItem = PresentationAssistantPopupSize.LARGE }
    }
    withContext(Dispatchers.EDT) {
      assertTrue(configurable.isModified, "the page is modified after an edit")
      configurable.apply()
    }

    awaitEquals(PresentationAssistantPopupSize.LARGE) { PresentationAssistantSettings.POPUP_SIZE.getValue() }
    withContext(Dispatchers.EDT) {
      assertFalse(configurable.isModified, "the page is not modified after the Apply")
      assertEquals(PresentationAssistantPopupSize.LARGE, sizeInput.selectedItem)
    }
  }

  @Test
  fun `a position from the page resets the offsets of a drag`(): Unit = timeoutRunBlocking(60.seconds) {
    val state = serviceAsync<PresentationAssistant>().configuration
    state.deltaX = 20
    state.deltaY = 30
    withContext(Dispatchers.EDT) {
      val page = configurable.createComponent()
      page.comboBoxOf<PresentationAssistantPopupAlignment>().selectedItem = PresentationAssistantPopupAlignment.TOP_LEFT
      configurable.apply()
    }

    awaitEquals(PresentationAssistantPopupAlignment.TOP_LEFT) { PresentationAssistantSettings.POPUP_ALIGNMENT.getValue() }
    assertEquals(null, state.deltaX)
    assertEquals(null, state.deltaY)
  }

  @Test
  fun `a value written through the key appears in the open page and keeps the unsaved edits`(): Unit = timeoutRunBlocking(60.seconds) {
    val (sizeInput, alignmentInput) = withContext(Dispatchers.EDT) {
      val page = configurable.createComponent()
      val alignmentInput = page.comboBoxOf<PresentationAssistantPopupAlignment>()
      alignmentInput.selectedItem = PresentationAssistantPopupAlignment.TOP_LEFT
      page.comboBoxOf<PresentationAssistantPopupSize>() to alignmentInput
    }

    val change = PresentationAssistantSettings.startChange()
      .setIfChanged(PresentationAssistantSettings.POPUP_SIZE, PresentationAssistantPopupSize.SMALL)
    change.apply()

    awaitEquals(PresentationAssistantPopupSize.SMALL) { withContext(Dispatchers.EDT) { sizeInput.selectedItem } }
    withContext(Dispatchers.EDT) {
      assertEquals(PresentationAssistantPopupAlignment.TOP_LEFT, alignmentInput.selectedItem, "the unsaved edit of the position")
      assertTrue(configurable.isModified, "the unsaved edit of the position keeps the page modified")
    }
  }

  @Test
  fun `an Apply writes only the changed input`(): Unit = timeoutRunBlocking(60.seconds) {
    val state = serviceAsync<PresentationAssistant>().configuration
    state.deltaX = 20
    state.deltaY = 30
    withContext(Dispatchers.EDT) {
      val page = configurable.createComponent()
      page.comboBoxOf<PresentationAssistantPopupSize>().selectedItem = PresentationAssistantPopupSize.LARGE
      configurable.apply()
    }

    awaitEquals(PresentationAssistantPopupSize.LARGE) { PresentationAssistantSettings.POPUP_SIZE.getValue() }
    val appContext = SettingsContext.app()
    assertEquals(emptyList<Any?>(), PresentationAssistantSettings.THEME.values(appContext), "the stored values of the theme")
    assertEquals(emptyList<Any?>(), PresentationAssistantSettings.POPUP_ALIGNMENT.values(appContext), "the stored values of the position")
    assertEquals(20, state.deltaX, "the dragged offsets stay")
    assertEquals(30, state.deltaY, "the dragged offsets stay")
  }

  @Test
  fun `a keymap label written through its per-OS key appears in the open page`(): Unit = timeoutRunBlocking(60.seconds) {
    val labelInput = withContext(Dispatchers.EDT) {
      val page = configurable.createComponent()
      val currentLabel = PresentationAssistantSettings.MAIN_KEYMAP_LABEL.getValue()
      UIUtil.findComponentsOfType(page, JBTextField::class.java).single { it.text == currentLabel }
    }

    val change = PresentationAssistantSettings.startChange()
      .setIfChanged(PresentationAssistantSettings.MAIN_KEYMAP_LABEL, "Changed label")
    change.apply()

    awaitEquals("Changed label") { withContext(Dispatchers.EDT) { labelInput.text } }
  }

  /** Returns the combo box of this page whose entries are of the type [T]. */
  private inline fun <reified T> JComponent.comboBoxOf(): ComboBox<*> =
    UIUtil.findComponentsOfType(this, ComboBox::class.java).single { it.model.size > 0 && it.model.getElementAt(0) is T }
}
