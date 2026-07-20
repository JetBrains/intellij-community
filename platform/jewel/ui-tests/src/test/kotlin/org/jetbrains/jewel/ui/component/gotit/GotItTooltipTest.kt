// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.component.gotit

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
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

        inPopup("open docs ↗").assertIsDisplayed()
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
                ) {
                    Box(Modifier.size(1.dp).semantics { testTag = "anchor" }.focusable())
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("anchor").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("anchor").performKeyPress(Key.Escape)
        rule.waitForIdle()

        assertTrue(escapedPressed)
        assertTrue(dismissed)
    }

    @Test
    fun `escape key calls onDismiss when no buttons are shown and onEscapePress is not set`() {
        var dismissed = false
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = { dismissed = true },
                    buttons = GotItButtons.None,
                ) {
                    Box(Modifier.size(1.dp).semantics { testTag = "anchor" }.focusable())
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("anchor").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("anchor").performKeyPress(Key.Escape)
        rule.waitForIdle()

        assertTrue(dismissed)
    }

    @Test
    fun `escape key calls onEscapePress and onDismiss when no buttons and onEscapePress is set`() {
        var escapePressed = false
        var dismissed = false
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    text = "Body",
                    visible = true,
                    onDismiss = { dismissed = true },
                    buttons = GotItButtons.None,
                    onEscapePress = { escapePressed = true },
                ) {
                    Box(Modifier.size(1.dp).semantics { testTag = "anchor" }.focusable())
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("anchor").requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag("anchor").performKeyPress(Key.Escape)
        rule.waitForIdle()

        assertTrue(escapePressed)
        assertTrue(dismissed)
    }

    @Test
    fun `escape key does not dismiss when buttons are shown and onEscapePress is not set`() {
        var dismissed = false
        rule.setContent {
            IntUiTheme { GotItTooltip(text = "Body", visible = true, onDismiss = { dismissed = true }) {} }
        }
        rule.waitForIdle()
        rule.onNode(isPopup()).performKeyPress(Key.Escape)
        rule.waitForIdle()

        assertFalse(dismissed)
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
        // Long repeating text guarantees 5+ lines at 280dp and fills lines close to the extended 328dp max
        val longText = "The quick brown fox jumps over the lazy dog. ".repeat(10)
        rule.setContent {
            IntUiTheme { GotItTooltip(body = buildGotItBody { append(longText) }, visible = true, onDismiss = {}) {} }
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasText(longText).and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        // Extension was triggered (width exceeded 280dp) and stayed within the extended max of 328dp
        assertTrue("Expected text wider than 280dp after extension, was $nodeWidth", nodeWidth > 280.dp)
        assertTrue("Expected text within 328dp extended max, was $nodeWidth", nodeWidth <= 328.dp)
    }

    @Test
    fun `width stays at maxWidth when maxWidth is explicitly set`() {
        // maxWidth disables auto-extension, so the content is capped at maxWidth regardless of line count
        val longText = "The quick brown fox jumps over the lazy dog. ".repeat(10)
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    body = buildGotItBody { append(longText) },
                    visible = true,
                    onDismiss = {},
                    maxWidth = 300.dp,
                ) {}
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasText(longText).and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        assertTrue("Expected text to not exceed maxWidth=300dp, was $nodeWidth", nodeWidth <= 300.dp)
        assertTrue("Expected text to fill close to maxWidth=300dp, was $nodeWidth", nodeWidth > 250.dp)
    }

    @Test
    fun `popup text width matches image width when image is present`() {
        // Long text fills the image-width column (300dp), confirming image drives content width
        val longText = "The quick brown fox jumps over the lazy dog. ".repeat(10)
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    body = buildGotItBody { append(longText) },
                    visible = true,
                    onDismiss = {},
                    image = GotItImage("drawables/test_gotit.png", null),
                ) {}
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasText(longText).and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        assertTrue("Expected text to fill close to image width (300dp), was $nodeWidth", nodeWidth > 250.dp)
        assertTrue("Expected text to not exceed image width (300dp), was $nodeWidth", nodeWidth <= 300.dp)
    }

    @Test
    fun `width does not extend beyond image width even with 5 or more lines`() {
        // Long text produces 5+ lines but extension is blocked — image width (300dp) wins
        val longText = "The quick brown fox jumps over the lazy dog. ".repeat(10)
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    body = buildGotItBody { append(longText) },
                    visible = true,
                    onDismiss = {},
                    image = GotItImage("drawables/test_gotit.png", null),
                ) {}
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasText(longText).and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        assertTrue("Expected no extension beyond image width (300dp), was $nodeWidth", nodeWidth <= 300.dp)
        assertTrue("Expected text to fill close to image width (300dp), was $nodeWidth", nodeWidth > 250.dp)
    }

    @Test
    fun `image width overrides maxWidth for content width`() {
        // Image is 300dp wide; maxWidth=50dp is ignored because image always takes priority
        val longText = "The quick brown fox jumps over the lazy dog. ".repeat(10)
        rule.setContent {
            IntUiTheme {
                GotItTooltip(
                    body = buildGotItBody { append(longText) },
                    visible = true,
                    onDismiss = {},
                    image = GotItImage("drawables/test_gotit.png", null),
                    maxWidth = 50.dp,
                ) {}
            }
        }
        rule.waitForIdle()
        val bounds = rule.onNode(hasText(longText).and(hasAnyAncestor(isPopup()))).getBoundsInRoot()
        val nodeWidth = bounds.right - bounds.left

        // Text width should be close to image width (300dp), well above the ignored maxWidth (50dp)
        assertTrue("Expected text wider than ignored maxWidth=50dp, was $nodeWidth", nodeWidth > 50.dp)
        assertTrue("Expected text within image width (300dp), was $nodeWidth", nodeWidth <= 300.dp)
    }

    private fun inPopup(text: String) = rule.onNode(hasText(text).and(hasAnyAncestor(isPopup())))
}
