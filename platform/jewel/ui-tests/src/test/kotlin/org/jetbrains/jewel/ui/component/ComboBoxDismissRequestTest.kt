// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.dp
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.JewelFlags
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test

/**
 * Both combo boxes suppress the *pointer* dismissal path while the pointer is over them, so that clicking the chevron
 * to close an open popup does not have the click-outside dismissal close it first and the chevron's own handler
 * immediately reopen it.
 *
 * That suppression belongs in `PopupProperties.dismissOnClickOutside`, which is what renderers consult for the pointer
 * path, and it must leave Escape alone. Expressing it by withholding `onDismissRequest` instead would suppress every
 * dismissal path at once, so Escape would be swallowed with nothing closing.
 */
@OptIn(ExperimentalJewelApi::class)
class ComboBoxDismissRequestTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `combo box allows pointer dismissal when the pointer is elsewhere`() {
        val renderer = recordPopup(hoverAt = null) { popupManager -> ComboBox(popupManager) }

        assertTrue(renderer.properties.isNotEmpty(), "The popup was never rendered")
        assertTrue(renderer.properties.last().dismissOnClickOutside, "An unhovered combo box must be dismissable")
        assertTrue(renderer.dismissRequests.last() != null, "A combo box must always be able to dismiss")
    }

    @Test
    fun `combo box suppresses only pointer dismissal while it is hovered`() {
        // The whole ComboBox drives the hover flag, so its centre is as good a hover target as the chevron.
        val renderer = recordPopup(hoverAt = { center }) { popupManager -> ComboBox(popupManager) }

        assertFalse(
            renderer.properties.last().dismissOnClickOutside,
            "A hovered combo box must opt out of the pointer dismissal path",
        )
        assertTrue(
            renderer.properties.last().dismissOnBackPress,
            "Hovering must not disable Escape: it is a separate dismissal path",
        )
        assertTrue(renderer.dismissRequests.last() != null, "A combo box must always be able to dismiss")
    }

    @Test
    fun `editable combo box allows pointer dismissal when the pointer is elsewhere`() {
        val renderer = recordPopup(hoverAt = null) { popupManager -> EditableComboBox(popupManager) }

        assertTrue(renderer.properties.isNotEmpty(), "The popup was never rendered")
        assertTrue(renderer.properties.last().dismissOnClickOutside, "An unhovered editable combo box is dismissable")
    }

    @Test
    fun `editable combo box suppresses only pointer dismissal while its chevron is hovered`() {
        // Unlike ComboBox, only the text field and the chevron drive the hover flags here, and the tagged node also
        // spans the popup, so aim at the chevron: the right-hand end of the first row.
        val renderer =
            recordPopup(hoverAt = { Offset(width - CHEVRON_INSET, CHEVRON_INSET) }) { popupManager ->
                EditableComboBox(popupManager)
            }

        assertFalse(
            renderer.properties.last().dismissOnClickOutside,
            "An editable combo box with a hovered chevron must opt out of the pointer dismissal path",
        )
        assertTrue(
            renderer.properties.last().dismissOnBackPress,
            "Hovering must not disable Escape: it is a separate dismissal path",
        )
    }

    @Composable
    private fun ComboBox(popupManager: PopupManager) {
        ComboBox(labelText = "Label", modifier = comboBoxModifier, popupManager = popupManager) {
            Text("Popup content")
        }
    }

    @Composable
    private fun EditableComboBox(popupManager: PopupManager) {
        EditableComboBox(
            textFieldState = remember { TextFieldState("Value") },
            modifier = comboBoxModifier,
            popupManager = popupManager,
        ) {
            Text("Popup content")
        }
    }

    private fun recordPopup(
        hoverAt: (MouseInjectionScope.() -> Offset)?,
        content: @Composable (PopupManager) -> Unit,
    ): RecordingPopupRenderer {
        val renderer = RecordingPopupRenderer()
        lateinit var popupManager: PopupManager
        val oldUseCustomPopupRenderer = JewelFlags.useCustomPopupRenderer
        JewelFlags.useCustomPopupRenderer = true
        try {
            composeRule.setContent {
                IntUiTheme {
                    CompositionLocalProvider(LocalPopupRenderer provides renderer) {
                        popupManager = remember { PopupManager() }
                        content(popupManager)
                    }
                }
            }
            composeRule.runOnIdle { popupManager.setPopupVisible(true) }
            composeRule.waitForIdle()

            if (hoverAt != null) {
                composeRule.onNodeWithTag(COMBO_BOX_TAG).performMouseInput {
                    // moveTo, not updatePointerTo: onHover keys off Enter, which only a dispatched move produces.
                    moveTo(hoverAt())
                    advanceEventTime()
                }
                composeRule.waitForIdle()
            }
        } finally {
            JewelFlags.useCustomPopupRenderer = oldUseCustomPopupRenderer
        }
        return renderer
    }

    private val comboBoxModifier
        get() = Modifier.testTag(COMBO_BOX_TAG).width(240.dp)
}

private const val COMBO_BOX_TAG = "Jewel.Test.ComboBox"
private const val CHEVRON_INSET = 8f
