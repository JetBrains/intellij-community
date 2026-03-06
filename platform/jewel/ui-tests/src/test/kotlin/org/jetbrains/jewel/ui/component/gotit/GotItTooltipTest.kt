// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.jetbrains.jewel.ui.component.interactions.performKeyPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class GotItTooltipTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `should not show tooltip popup when visible is false`() {
        rule.setContent { IntUiTheme { GotItTooltip(text = "Body text", visible = false, onDismiss = {}) {} } }

        inPopup("Body text").assertDoesNotExist()
    }

    @Test
    fun `should show tooltip popup when visible is true`() {
        rule.setContent { IntUiTheme { GotItTooltip(text = "Body text", visible = true, onDismiss = {}) {} } }

        inPopup("Body text").assertIsDisplayed()
    }

    @Test
    fun `should hide tooltip when visible changes to false`() {
        var visible by mutableStateOf(true)
        rule.setContent { IntUiTheme { GotItTooltip(text = "Body text", visible = visible, onDismiss = {}) {} } }

        inPopup("Body text").assertIsDisplayed()

        visible = false
        rule.waitForIdle()

        inPopup("Body text").assertDoesNotExist()
    }

    @Test
    fun `tooltip appears when visible changes from false to true`() {
        var visible by mutableStateOf(false)
        rule.setContent { IntUiTheme { GotItTooltip(text = "Body text", visible = visible, onDismiss = {}) {} } }

        inPopup("Body text").assertDoesNotExist()

        visible = true
        rule.waitForIdle()

        inPopup("Body text").assertIsDisplayed()
    }

    @Test
    fun `should render text in popup for plain string overload`() {
        rule.setContent { IntUiTheme { GotItTooltip(text = "Hello world", visible = true, onDismiss = {}) {} } }

        inPopup("Hello world").assertIsDisplayed()
    }

    @Test
    fun `plain string overload renders correctly when body is empty string`() {
        rule.setContent { IntUiTheme { GotItTooltip(text = "", visible = true, onDismiss = {}) {} } }
        // The popup exists but the body text node may be empty — just verify popup is shown

        rule.onNode(isPopup()).assertIsDisplayed()
    }

    @Test
    fun `should render plain segment text`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(body = buildGotItBody { append("Plain segment") }, visible = true, onDismiss = {}) {}
            }
        }

        inPopup("Plain segment").assertIsDisplayed()
    }

    @Test
    fun `body with bold segment text appears in popup`() {
        rule.setContent {
            IntUiTheme { GotItTooltip(body = buildGotItBody { bold("Bold text") }, visible = true, onDismiss = {}) {} }
        }

        inPopup("Bold text").assertIsDisplayed()
    }

    @Test
    fun `body with code segment text appears in popup`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(body = buildGotItBody { code("someFunction()") }, visible = true, onDismiss = {}) {}
            }
        }

        inPopup("someFunction()").assertIsDisplayed()
    }

    @Test
    fun `body with inline link segment text appears in popup`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(body = buildGotItBody { link("click here") {} }, visible = true, onDismiss = {}) {}
            }
        }

        inPopup("click here").assertIsDisplayed()
    }

    @Test
    fun `should show browser link text segment in body when declared`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    body = buildGotItBody { browserLink("open docs", "https://example.com") },
                    visible = true,
                    onDismiss = {},
                ) {}
            }
        }

        inPopup("open docs").assertIsDisplayed()
    }

    @Test
    fun `should render all texts when body has multiple mixed segments`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    body =
                        buildGotItBody {
                            append("Press ")
                            bold("Resume")
                            append(" to continue")
                        },
                    visible = true,
                    onDismiss = {},
                ) {}
            }
        }

        // The three segments are merged into a single AnnotatedString in one Text node
        inPopup("Press Resume to continue").assertIsDisplayed()
    }

    @Test
    fun `should preserve segment order when body is built with chained calls`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    body =
                        buildGotItBody {
                            append("A")
                            bold("B")
                            code("C")
                        },
                    visible = true,
                    onDismiss = {},
                ) {}
            }
        }

        inPopup("ABC").assertIsDisplayed()
    }

    @Test
    fun `should show header text when non-empty`() {
        rule.setContent {
            IntUiTheme { GotItTooltip(text = "Body", visible = true, onDismiss = {}, header = "My Header") {} }
        }

        inPopup("My Header").assertIsDisplayed()
    }

    @Test
    fun `should not render header when it is an empty string`() {
        rule.setContent { IntUiTheme { GotItTooltip(text = "Body", visible = true, onDismiss = {}, header = "") {} } }

        // Popup is shown but there's no header node
        inPopup("Body").assertIsDisplayed()
        rule.onNodeWithText("").assertDoesNotExist()
    }

    @Test
    fun `should show default Got it button if button not provided`() {
        rule.setContent { IntUiTheme { GotItTooltip(text = "Body", visible = true, onDismiss = {}) {} } }

        inPopup("Got it").assertIsDisplayed()
    }

    @Test
    fun `should show correct custom primary button label`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = {},
                    buttons = GotItButtons(primary = GotItButton("Understood")),
                ) {}
            }
        }

        inPopup("Understood").assertIsDisplayed()
    }

    @Test
    fun `should display secondary button when provided`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = {},
                    buttons = GotItButtons(primary = GotItButton.Default, secondary = GotItButton("Learn more")),
                ) {}
            }
        }

        inPopup("Learn more").assertIsDisplayed()
    }

    @Test
    fun `should not render primary button when button primary is null`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(text = "Body", visible = true, onDismiss = {}, buttons = GotItButtons(primary = null)) {}
            }
        }

        inPopup("Got it").assertDoesNotExist()
    }

    @Test
    fun `should call onDismiss when primary button is clicked`() {
        var dismissed = false
        rule.setContent {
            IntUiTheme { GotItTooltip(text = "Body", visible = true, onDismiss = { dismissed = true }) {} }
        }

        inPopup("Got it").performClick()

        rule.waitForIdle()

        assertTrue(dismissed)
    }

    @Test
    fun `clicking secondary button calls onDismiss`() {
        var dismissed = false
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = { dismissed = true },
                    buttons = GotItButtons(primary = GotItButton.Default, secondary = GotItButton("Skip")),
                ) {}
            }
        }

        inPopup("Skip").performClick()

        rule.waitForIdle()

        assertTrue(dismissed)
    }

    @Test
    fun `primary button side effect runs before onDismiss`() {
        val order = mutableListOf<String>()
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = { order.add("dismiss") },
                    buttons = GotItButtons(primary = GotItButton("OK") { order.add("side_effect") }),
                ) {}
            }
        }

        inPopup("OK").performClick()

        rule.waitForIdle()

        assertEquals(listOf("side_effect", "dismiss"), order)
    }

    @Test
    fun `secondary button side effect runs before onDismiss`() {
        val order = mutableListOf<String>()
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = { order.add("dismiss") },
                    buttons =
                        GotItButtons(
                            primary = GotItButton.Default,
                            secondary = GotItButton("Skip") { order.add("side_effect") },
                        ),
                ) {}
            }
        }

        inPopup("Skip").performClick()

        rule.waitForIdle()

        assertEquals(listOf("side_effect", "dismiss"), order)
    }

    @Test
    fun `regular link label is shown when link is provided`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = {},
                    link = GotItLink.Regular("Learn more") {},
                ) {}
            }
        }

        inPopup("Learn more").assertIsDisplayed()
    }

    @Test
    fun `browser link label is shown when link is provided`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = {},
                    link = GotItLink.Browser("Open docs", "https://example.com"),
                ) {}
            }
        }

        inPopup("Open docs").assertIsDisplayed()
    }

    @Test
    fun `no link section when link is null`() {
        rule.setContent { IntUiTheme { GotItTooltip(text = "Body", visible = true, onDismiss = {}, link = null) {} } }

        // Popup is present but no extra link node
        inPopup("Body").assertIsDisplayed()
        rule.onNodeWithText("Learn more").assertDoesNotExist()
    }

    @Test
    fun `clicking regular link calls its action`() {
        var clicked = false
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = {},
                    link = GotItLink.Regular("Click me") { clicked = true },
                ) {}
            }
        }

        inPopup("Click me").performClick()

        rule.waitForIdle()

        assertTrue(clicked)
    }

    @Test
    fun `buttons are hidden when timeout is set`() {
        rule.setContent {
            IntUiTheme { GotItTooltip(text = "Body", visible = true, onDismiss = {}, timeout = 5000.milliseconds) {} }
        }

        inPopup("Got it").assertDoesNotExist()
    }

    @Test
    fun `onDismiss is called after timeout elapses`() {
        var dismissed = false
        rule.mainClock.autoAdvance = false
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = { dismissed = true },
                    timeout = 500.milliseconds,
                ) {}
            }
        }
        rule.waitForIdle()

        assertFalse(dismissed)

        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        assertTrue(dismissed)
    }

    @Test
    fun `onShown is called when tooltip becomes visible`() {
        var shown = false
        rule.setContent {
            IntUiTheme { GotItTooltip(text = "Body", visible = true, onDismiss = {}, onShow = { shown = true }) {} }
        }
        rule.waitForIdle()

        assertTrue(shown)
    }

    @Test
    fun `onShown is not called when tooltip starts hidden`() {
        var shown = false
        rule.setContent {
            IntUiTheme { GotItTooltip(text = "Body", visible = false, onDismiss = {}, onShow = { shown = true }) {} }
        }
        rule.waitForIdle()

        assertFalse(shown)
    }

    @Test
    fun `escape key triggers onEscapePressed and onDismiss when set`() {
        var escapedPressed = false
        var dismissed = false
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = { dismissed = true },
                    onEscapePress = { escapedPressed = true },
                ) {}
            }
        }
        rule.waitForIdle()
        rule.onNode(isPopup()).performKeyPress(Key.Escape)
        rule.waitForIdle()

        assertTrue(escapedPressed)
        assertTrue(dismissed)
    }

    @Test
    fun `step number is rendered inside the popup`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(text = "Body", visible = true, onDismiss = {}, iconOrStep = GotItIconOrStep.Step(3)) {}
            }
        }

        inPopup("03").assertIsDisplayed()
    }

    @Test
    fun `default width is 280dp for short content`() {
        rule.setContent { IntUiTheme { GotItTooltip(text = "Short", visible = true, onDismiss = {}) {} } }
        rule.waitForIdle()
        // The body text node is constrained to currentWidth (280dp for short content).
        val bounds = rule.onNode(hasText("Short").and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        assertTrue("Expected text width to be less than 328dp for short content, was $nodeWidth", nodeWidth < 328.dp)
    }

    @Test
    fun `width extends to 328dp when body text spans 5 or more lines`() {
        // "a\nb\nc\nd\ne" forces 5 lines regardless of font, triggering the 280→328 dp extension
        rule.setContent {
            IntUiTheme {
                GotItTooltip(body = buildGotItBody { append("a\nb\nc\nd\ne") }, visible = true, onDismiss = {}) {}
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasText("a\nb\nc\nd\ne").and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        assertTrue("Expected text width to be 328dp after extension, was $nodeWidth", nodeWidth == 328.dp)
    }

    @Test
    fun `width stays at 280dp when maxWidth is explicitly set`() {
        // maxWidth disables auto-extension, so even 5+ lines stays at maxWidth
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    body = buildGotItBody { append("a\nb\nc\nd\ne") },
                    visible = true,
                    onDismiss = {},
                    maxWidth = 300.dp,
                ) {}
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasText("a\nb\nc\nd\ne").and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        // maxWidth disables extension — width should be around 300dp, definitely less than 328dp
        assertTrue(
            "Expected text width to stay below 328dp with explicit maxWidth=300dp, was $nodeWidth",
            nodeWidth == 300.dp,
        )
    }

    @Test
    fun `popup width should have the exact same width as the image`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Hi this is a test",
                    visible = true,
                    onDismiss = {},
                    image = GotItImage("drawables/test_gotit.png", null),
                ) {}
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasText("Hi this is a test").and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        assertTrue(
            "Expected popup to have the same width as the image provided, but was $nodeWidth",
            nodeWidth == 300.dp,
        )
    }

    @Test
    fun `width does not extend to 328dp when image 300p in width is present with 5 or more lines`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    body = buildGotItBody { append("a\nb\nc\nd\ne") },
                    visible = true,
                    onDismiss = {},
                    image = GotItImage("drawables/test_gotit.png", null),
                ) {}
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasText("a\nb\nc\nd\ne").and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        assertTrue(
            "Expected no auto-extension (< 328dp) when image (300dp width) is present, was $nodeWidth",
            nodeWidth == 300.dp,
        )
    }

    @Test
    fun `image with 300p in width overrides maxWidth for content width`() {
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    body = buildGotItBody { append("Body") },
                    visible = true,
                    onDismiss = {},
                    image = GotItImage("drawables/test_gotit.png", null),
                    maxWidth = 50.dp,
                ) {}
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasText("Body").and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        assertTrue("Expected image width (300dp) to override maxWidth=50dp, was $nodeWidth", nodeWidth == 300.dp)
    }

    private fun inPopup(text: String) = rule.onNode(hasText(text).and(hasAnyAncestor(isPopup())))
}
