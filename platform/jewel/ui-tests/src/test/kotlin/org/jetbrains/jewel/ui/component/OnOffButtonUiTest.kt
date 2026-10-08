// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.styling.OnOffButtonMetrics
import org.jetbrains.jewel.ui.component.styling.OnOffButtonStyle
import org.jetbrains.jewel.ui.theme.onOffButtonStyle
import org.junit.Rule
import org.junit.Test

class OnOffButtonUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `should have switch role`() {
        rule.setContent { IntUiTheme { OnOffButton(checked = false, onCheckedChange = {}, modifier = tagged) } }

        rule.onNodeWithTag(TAG).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
    }

    @Test
    fun `checked button should report on state`() {
        rule.setContent { IntUiTheme { OnOffButton(checked = true, onCheckedChange = {}, modifier = tagged) } }

        rule.onNodeWithTag(TAG).assertIsOn()
    }

    @Test
    fun `unchecked button should report off state`() {
        rule.setContent { IntUiTheme { OnOffButton(checked = false, onCheckedChange = {}, modifier = tagged) } }

        rule.onNodeWithTag(TAG).assertIsOff()
    }

    @Test
    fun `clicking unchecked button should request checked`() {
        var requested: Boolean? = null
        rule.setContent {
            IntUiTheme { OnOffButton(checked = false, onCheckedChange = { requested = it }, modifier = tagged) }
        }

        rule.onNodeWithTag(TAG).performClick()
        rule.waitForIdle()

        assertEquals(true, requested)
    }

    @Test
    fun `clicking checked button should request unchecked`() {
        var requested: Boolean? = null
        rule.setContent {
            IntUiTheme { OnOffButton(checked = true, onCheckedChange = { requested = it }, modifier = tagged) }
        }

        rule.onNodeWithTag(TAG).performClick()
        rule.waitForIdle()

        assertEquals(false, requested)
    }

    @Test
    fun `hoisted state should follow clicks`() {
        var checked by mutableStateOf(false)
        rule.setContent {
            IntUiTheme { OnOffButton(checked = checked, onCheckedChange = { checked = it }, modifier = tagged) }
        }

        rule.onNodeWithTag(TAG).performClick()
        rule.waitForIdle()
        assertTrue(checked)
        rule.onNodeWithTag(TAG).assertIsOn()

        rule.onNodeWithTag(TAG).performClick()
        rule.waitForIdle()
        assertFalse(checked)
        rule.onNodeWithTag(TAG).assertIsOff()
    }

    @Test
    fun `enabled button should be enabled`() {
        rule.setContent { IntUiTheme { OnOffButton(checked = false, onCheckedChange = {}, modifier = tagged) } }

        rule.onNodeWithTag(TAG).assertIsEnabled()
    }

    @Test
    fun `disabled button should not be enabled`() {
        rule.setContent {
            IntUiTheme { OnOffButton(checked = false, onCheckedChange = {}, modifier = tagged, enabled = false) }
        }

        rule.onNodeWithTag(TAG).assertIsNotEnabled()
    }

    @Test
    fun `clicking disabled button should not call onCheckedChange`() {
        var requested: Boolean? = null
        rule.setContent {
            IntUiTheme {
                OnOffButton(checked = false, onCheckedChange = { requested = it }, modifier = tagged, enabled = false)
            }
        }

        rule.onNodeWithTag(TAG).performClick()
        rule.waitForIdle()

        assertNull(requested)
    }

    @Test
    fun `default size should include room for the focus ring`() {
        rule.setContent { IntUiTheme { OnOffButton(checked = false, onCheckedChange = {}, modifier = tagged) } }

        // The size of the IJP toggle*.svg images, which include the focus ring area
        rule.onNodeWithTag(TAG).assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(22.dp)
    }

    @Test
    fun `gaining focus should not change size`() {
        rule.setContent { IntUiTheme { OnOffButton(checked = true, onCheckedChange = {}, modifier = tagged) } }

        rule.onNodeWithTag(TAG).requestFocus()
        rule.waitForIdle()

        rule.onNodeWithTag(TAG).assertIsFocused().assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(22.dp)
    }

    @Test
    fun `size should follow custom metrics`() {
        rule.setContent {
            IntUiTheme {
                val style =
                    OnOffButtonStyle(
                        metrics = OnOffButtonMetrics(size = DpSize(48.dp, 33.dp)),
                        icons = JewelTheme.onOffButtonStyle.icons,
                    )
                OnOffButton(checked = false, onCheckedChange = {}, modifier = tagged, style = style)
            }
        }

        rule.onNodeWithTag(TAG).assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(33.dp)
    }

    @Test
    fun `state copy should keep interaction flags when checked changes`() {
        val state = OnOffButtonState.of(checked = false, focused = true, hovered = true, pressed = true)

        val copy = state.copy(checked = true)

        assertTrue(copy.isSelected)
        assertTrue(copy.isFocused)
        assertTrue(copy.isHovered)
        assertTrue(copy.isPressed)
        assertTrue(copy.isEnabled)
    }

    @Test
    fun `state copy should keep checked when enabled changes`() {
        val state = OnOffButtonState.of(checked = true)

        val copy = state.copy(enabled = false)

        assertTrue(copy.isSelected)
        assertFalse(copy.isEnabled)
    }

    private companion object {
        const val TAG = "on-off-button"
        val tagged = Modifier.testTag(TAG)
    }
}
