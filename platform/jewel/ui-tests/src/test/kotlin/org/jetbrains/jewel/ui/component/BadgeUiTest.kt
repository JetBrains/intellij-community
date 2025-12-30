// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.styling.BadgeColors
import org.jetbrains.jewel.ui.component.styling.BadgeMetrics
import org.jetbrains.jewel.ui.component.styling.BadgeStyle
import org.jetbrains.jewel.ui.theme.badgeStyle
import org.junit.Rule
import org.junit.Test

class BadgeUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `should render badge with text`() {
        rule.setContent { IntUiTheme { Badge("New") } }

        rule.onNodeWithText("New").assertExists().assertIsDisplayed()
    }

    @Test
    fun `should apply provided modifier correctly`() {
        rule.setContent { IntUiTheme { Badge("Beta", modifier = Modifier.testTag("badge-tag")) } }

        rule.onNodeWithText("Beta").assertExists()
        rule.onNodeWithTag("badge-tag").assertExists()
    }

    @Test
    fun `non-clickable badge should not have click action`() {
        rule.setContent { IntUiTheme { Badge("New", onClick = null) } }

        rule.onNodeWithText("New").assertHasNoClickAction()
    }

    @Test
    fun `non-clickable badge should not have button role`() {
        rule.setContent { IntUiTheme { Badge("New") } }

        val node = rule.onNodeWithText("New")
        node.assertExists()

        val hasRole = node.fetchSemanticsNode().config.contains(SemanticsProperties.Role)
        assertFalse(hasRole, "Non-clickable badge should not have a button role")
    }

    @Test
    fun `clickable badge should have click action`() {
        rule.setContent { IntUiTheme { Badge("New", onClick = {}) } }

        rule.onNodeWithText("New").assertHasClickAction()
    }

    @Test
    fun `clickable badge should have button role`() {
        rule.setContent { IntUiTheme { Badge("New", onClick = {}) } }

        rule.onNodeWithText("New").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun `clicking badge should trigger onClick`() {
        var clicked = false
        rule.setContent { IntUiTheme { Badge("New", onClick = { clicked = true }) } }

        assertFalse(clicked, "Badge should not be clicked initially")

        rule.onNodeWithText("New").performClick()
        rule.waitForIdle()

        assertTrue(clicked, "Badge onClick should have been triggered")
    }

    @Test
    fun `enabled badge should be enabled`() {
        rule.setContent { IntUiTheme { Badge("New", onClick = {}, enabled = true) } }

        rule.onNodeWithText("New").assertIsEnabled()
    }

    @Test
    fun `disabled badge should not be enabled`() {
        rule.setContent { IntUiTheme { Badge("New", onClick = {}, enabled = false) } }

        rule.onNodeWithText("New").assertIsNotEnabled()
    }

    @Test
    fun `disabled badge should not trigger onClick`() {
        var clicked = false
        rule.setContent { IntUiTheme { Badge("New", onClick = { clicked = true }, enabled = false) } }

        rule.onNodeWithText("New").performClick()
        rule.waitForIdle()

        assertFalse(clicked, "Disabled badge should not trigger onClick")
    }

    @Test
    fun `badge should support custom background color`() {
        val customGreen = Color(0xFF4CAF50)
        rule.setContent {
            IntUiTheme {
                Badge(
                    text = "Success",
                    style =
                        BadgeStyle(
                            colors =
                                BadgeColors(
                                    background = SolidColor(customGreen),
                                    backgroundDisabled = SolidColor(Color.Gray),
                                    backgroundFocused = SolidColor(customGreen),
                                    backgroundPressed = SolidColor(customGreen),
                                    backgroundHovered = SolidColor(customGreen),
                                    content = Color.White,
                                    contentDisabled = Color.Gray,
                                    contentFocused = Color.White,
                                    contentPressed = Color.White,
                                    contentHovered = Color.White,
                                ),
                            metrics = JewelTheme.badgeStyle.metrics,
                        ),
                )
            }
        }

        rule.onNodeWithText("Success").assertExists().assertIsDisplayed()
    }

    @Test
    fun `badge should support brush background`() {
        rule.setContent {
            IntUiTheme {
                Badge(
                    text = "Gradient",
                    style =
                        BadgeStyle(
                            colors =
                                BadgeColors(
                                    background = Brush.linearGradient(colors = listOf(Color.Blue, Color.Cyan)),
                                    backgroundDisabled = SolidColor(Color.Gray),
                                    backgroundFocused = Brush.linearGradient(colors = listOf(Color.Blue, Color.Cyan)),
                                    backgroundPressed = Brush.linearGradient(colors = listOf(Color.Blue, Color.Cyan)),
                                    backgroundHovered = Brush.linearGradient(colors = listOf(Color.Blue, Color.Cyan)),
                                    content = Color.White,
                                    contentDisabled = Color.Gray,
                                    contentFocused = Color.White,
                                    contentPressed = Color.White,
                                    contentHovered = Color.White,
                                ),
                            metrics = JewelTheme.badgeStyle.metrics,
                        ),
                )
            }
        }

        rule.onNodeWithText("Gradient").assertExists().assertIsDisplayed()
    }

    @Test
    fun `badge should support custom corner radius via metrics`() {
        rule.setContent {
            IntUiTheme {
                Badge(
                    text = "Rounded",
                    style =
                        BadgeStyle(
                            colors = JewelTheme.badgeStyle.colors,
                            metrics =
                                BadgeMetrics(
                                    cornerSize = CornerSize(8.dp),
                                    padding = JewelTheme.badgeStyle.metrics.padding,
                                    minSize = JewelTheme.badgeStyle.metrics.minSize,
                                ),
                        ),
                    modifier = Modifier.testTag("rounded-badge"),
                )
            }
        }

        rule.onNodeWithTag("rounded-badge").assertExists().assertIsDisplayed()
        rule.onNodeWithText("Rounded").assertExists()
    }
}
