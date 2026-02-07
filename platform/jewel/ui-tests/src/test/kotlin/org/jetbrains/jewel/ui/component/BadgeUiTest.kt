// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component

import androidx.compose.ui.Modifier
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test

class BadgeUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `should render badge with text`() {
        rule.setContent { IntUiTheme { Badge { Text("New") } } }

        rule.onNodeWithText("New").assertExists().assertIsDisplayed()
    }

    @Test
    fun `should apply provided modifier correctly`() {
        rule.setContent { IntUiTheme { Badge(modifier = Modifier.testTag("badge-tag")) { Text("Beta") } } }

        rule.onNodeWithText("Beta").assertExists()
        rule.onNodeWithTag("badge-tag").assertExists()
    }

    @Test
    fun `non-clickable badge should not have click action`() {
        rule.setContent { IntUiTheme { Badge(onClick = null) { Text("New") } } }

        rule.onNodeWithText("New").assertHasNoClickAction()
    }

    @Test
    fun `non-clickable badge should not have button role`() {
        rule.setContent { IntUiTheme { Badge(onClick = null) { Text("New") } } }

        val node = rule.onNodeWithText("New")
        node.assertExists()

        val hasRole = node.fetchSemanticsNode().config.contains(SemanticsProperties.Role)
        assertFalse(hasRole, "Non-clickable badge should not have a button role")
    }

    @Test
    fun `clickable badge should have click action`() {
        rule.setContent { IntUiTheme { Badge(onClick = {}) { Text("New") } } }

        rule.onNodeWithText("New").assertHasClickAction()
    }

    @Test
    fun `clickable badge should have button role`() {
        rule.setContent { IntUiTheme { Badge(onClick = {}) { Text("New") } } }

        rule.onNodeWithText("New").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun `clicking badge should trigger onClick`() {
        var clicked = false
        rule.setContent { IntUiTheme { Badge(onClick = { clicked = true }) { Text("New") } } }

        assertFalse(clicked, "Badge should not be clicked initially")

        rule.onNodeWithText("New").performClick()
        rule.waitForIdle()

        assertTrue(clicked, "Badge onClick should have been triggered")
    }

    @Test
    fun `enabled badge should be enabled`() {
        rule.setContent { IntUiTheme { Badge(onClick = {}, enabled = true) { Text("New") } } }

        rule.onNodeWithText("New").assertIsEnabled()
    }

    @Test
    fun `disabled badge should not be enabled`() {
        rule.setContent { IntUiTheme { Badge(onClick = {}, enabled = false) { Text("New") } } }

        rule.onNodeWithText("New").assertIsNotEnabled()
    }

    @Test
    fun `disabled badge should not trigger onClick`() {
        var clicked = false
        rule.setContent { IntUiTheme { Badge(onClick = { clicked = true }, enabled = false) { Text("New") } } }

        rule.onNodeWithText("New").performClick()
        rule.waitForIdle()

        assertFalse(clicked, "Disabled badge should not trigger onClick")
    }
}
